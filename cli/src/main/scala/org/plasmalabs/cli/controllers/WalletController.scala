package org.plasmalabs.cli.controllers

import cats.data.OptionT
import cats.effect.kernel.{Resource, Sync}
import com.google.protobuf.ByteString
import org.plasmalabs.cli.impl.{WalletAlgebra, WalletManagementUtils, WalletModeHelper}
import org.plasmalabs.cli.{DigestType, PlasmaCliParams, Sha256}
import org.plasmalabs.crypto.hash.Blake2b256
import org.plasmalabs.indexer.services.{Txo, TxoState}
import org.plasmalabs.quivr.models.{Digest, Preimage, Proposition, VerificationKey}
import org.plasmalabs.sdk.builders.TransactionBuilderApi
import org.plasmalabs.sdk.codecs.AddressCodecs
import org.plasmalabs.sdk.constants.NetworkConstants
import org.plasmalabs.sdk.dataApi
import org.plasmalabs.sdk.models.{Indices, LockAddress, LockId}
import org.plasmalabs.sdk.utils.Encoding
import org.plasmalabs.sdk.wallet.WalletApi
import org.plasmalabs.shared.models.{AssetTokenBalanceDTO, GroupTokenBalanceDTO, LvlBalance, SeriesTokenBalanceDTO}

import java.io.{File, PrintWriter}

class WalletController[F[_]: Sync](
  walletStateAlgebra:    dataApi.WalletStateAlgebra[F],
  walletManagementUtils: WalletManagementUtils[F],
  walletApi:             WalletApi[F],
  walletAlgebra:         WalletAlgebra[F],
  indexerQueryAlgebra:   dataApi.IndexerQueryAlgebra[F]
) {

  def addSecret(
    secretTxt: String,
    digest:    DigestType
  ): F[Either[String, String]] = {
    import org.plasmalabs.crypto.hash.implicits.sha256Hash
    import cats.implicits._
    val paddedSecret = secretTxt.getBytes() ++ Array
      .fill(32 - secretTxt.getBytes().length)(0.toByte)
    val hashedSecret =
      if (digest == Sha256)
        sha256Hash
          .hash(
            paddedSecret
          )
          .value
      else
        (new Blake2b256).hash(
          paddedSecret
        )
    val propDigest = Proposition.Digest(
      digest.digestIdentifier,
      Digest(
        ByteString.copyFrom(
          hashedSecret
        )
      )
    )
    for {
      somePreimage <- walletStateAlgebra.getPreimage(
        propDigest
      )
      res <-
        if (somePreimage.isEmpty)
          walletStateAlgebra
            .addPreimage(
              Preimage(
                ByteString.copyFrom(secretTxt.getBytes()),
                ByteString.copyFrom(
                  Array.fill(32 - secretTxt.getBytes().length)(0.toByte)
                )
              ),
              propDigest
            )
            .map { _ =>
              Right(
                "Secret added. Hash: " + Encoding.encodeToHex(hashedSecret)
              )
            }
        else
          Sync[F].pure(Left("Secret already exists"))
    } yield res
  }

  def getPreimage(
    digest:    DigestType,
    digestTxt: String
  ): F[Either[String, String]] = {
    import cats.implicits._
    for {
      decodedDigest <- Sync[F].fromEither(Encoding.decodeFromHex(digestTxt))
      propDigest = Proposition.Digest(
        digest.digestIdentifier,
        Digest(
          ByteString.copyFrom(
            decodedDigest
          )
        )
      )
      somePreimage <- walletStateAlgebra.getPreimage(
        propDigest
      )
    } yield somePreimage match {
      case Some(preimage) =>
        Right(
          "Preimage: " + new String(preimage.input.toByteArray)
        )
      case None => Left("Preimage not found.")
    }
  }

  def importVk(
    networkId:      Int,
    inputVks:       Seq[File],
    keyfile:        String,
    password:       String,
    templateName:   String,
    fellowshipName: String
  ): F[Either[String, String]] = {
    import cats.implicits._
    import TransactionBuilderApi.implicits._
    import org.plasmalabs.sdk.common.ContainsEvidence.Ops
    import org.plasmalabs.sdk.common.ContainsImmutable.instances._
    for {
      keyAndEncodedKeys <- (inputVks
        .map { file =>
          Resource
            .make(Sync[F].delay(scala.io.Source.fromFile(file)))(file => Sync[F].delay(file.close()))
            .use { file =>
              Sync[F].blocking(file.getLines().toList.mkString)
            }
        })
        .sequence
        .flatMap(
          _.map(_.trim())
            .filterNot(_.isEmpty())
            .map(
              // TODO: replace with proper serialization
              vk =>
                // we derive the key once
                walletApi
                  .deriveChildVerificationKey(
                    VerificationKey.parseFrom(
                      Encoding.decodeFromBase58(vk).toOption.get
                    ),
                    1
                  )
                  .map(x => (x, vk))
            )
            .sequence
        )
      lockTempl <- walletStateAlgebra
        .getLockTemplate(templateName)
        .map(_.get) // it exists because of the validation
      // we need to get the corresponding vk
      indices <- walletStateAlgebra.getNextIndicesForFunds(
        fellowshipName,
        templateName
      )
      keypair        <- walletManagementUtils.loadKeys(keyfile, password)
      deriveChildKey <- walletApi.deriveChildKeys(keypair, indices.get)
      deriveChildKeyBase <- walletApi.deriveChildKeysPartial(
        keypair,
        indices.get.x,
        indices.get.y
      )
      deriveChildKeyString = Encoding.encodeToBase58(
        deriveChildKeyBase.vk.toByteArray
      )
      errorOrLock <- lockTempl.build(
        keyAndEncodedKeys.toList.map(x => x._1)
      )
      lockAddress = LockAddress(
        networkId,
        NetworkConstants.MAIN_LEDGER_ID,
        LockId(errorOrLock.toOption.get.sizedEvidence.digest.value)
      )
      _ <- walletStateAlgebra.updateWalletState(
        Encoding.encodeToBase58Check(
          errorOrLock.toOption.get.getPredicate.toByteArray
        ), // lockPredicate
        lockAddress.toBase58(), // lockAddress
        Some("ExtendedEd25519"),
        Some(Encoding.encodeToBase58(deriveChildKey.vk.toByteArray)),
        indices.get
      )
      _ <- walletStateAlgebra.addEntityVks(
        fellowshipName,
        templateName,
        keyAndEncodedKeys.toList.map(_._2)
      )
      _ <- lockTempl.build(keyAndEncodedKeys.toList.map(_._1))
    } yield Right("Successfully imported verification keys")
  }

  def exportFinalVk(
    keyFile:        String,
    password:       String,
    outputFile:     String,
    fellowshipName: String,
    templateName:   String,
    interaction:    Int
  ): F[Either[String, String]] = {
    import cats.implicits._
    (for {
      indices <- OptionT(
        walletStateAlgebra.getCurrentIndicesForFunds(
          fellowshipName,
          templateName,
          None
        )
      )
      keypair <- OptionT(
        walletManagementUtils
          .loadKeys(keyFile, password)
          .map(x => Option(x))
      )
      deriveChildKey <- OptionT(
        walletApi
          .deriveChildKeys(keypair, indices.copy(z = interaction))
          .map(
            Option(_)
          )
      )
    } yield Resource
      .make(Sync[F].delay(new PrintWriter(outputFile)))(file =>
        Sync[F].delay(file.flush()) >> Sync[F].delay(file.close())
      )
      .use { file =>
        for {
          _ <- Sync[F].blocking(
            file.write(Encoding.encodeToBase58(deriveChildKey.vk.toByteArray))
          )
        } yield ()
      }).value.map(_.get).flatten.map(_ => Right("Verification key exported"))
  }

  def exportVk(
    keyFile:        String,
    password:       String,
    outputFile:     String,
    fellowshipName: String,
    templateName:   String
  ): F[Either[String, String]] = {
    import cats.implicits._
    (for {
      indices <- OptionT(
        walletStateAlgebra.getCurrentIndicesForFunds(
          fellowshipName,
          templateName,
          None
        )
      )
      keypair <- OptionT(
        walletManagementUtils
          .loadKeys(keyFile, password)
          .map(x => Option(x))
      )
      deriveChildKey <- OptionT(
        walletApi
          .deriveChildKeysPartial(keypair, indices.x, indices.y)
          .map(
            Option(_)
          )
      )
    } yield Resource
      .make(Sync[F].delay(new PrintWriter(outputFile)))(file =>
        Sync[F].delay(file.flush()) >> Sync[F].delay(file.close())
      )
      .use { file =>
        for {
          _ <- Sync[F].blocking(
            file.write(Encoding.encodeToBase58(deriveChildKey.vk.toByteArray))
          )
        } yield ()
      }).value.map(_.get).flatten.map(_ => Right("Verification key exported"))
  }

  def createWalletFromParams(
    params: PlasmaCliParams
  ): F[Either[String, String]] = {
    import cats.implicits._
    walletAlgebra
      .createWalletFromParams(
        params.network.networkId,
        NetworkConstants.MAIN_LEDGER_ID,
        params.password,
        params.somePassphrase,
        params.someOutputFile,
        params.someMnemonicFile
      )
      .map(_ => Right("Wallet created"))
  }

  def listInteractions(
    fromFellowship: String,
    fromTemplate:   String
  ): F[Either[String, String]] = {
    import cats.implicits._
    walletStateAlgebra
      .getInteractionList(fromFellowship, fromTemplate)
      .map(_ match {
        case Some(interactions) =>
          Right(
            interactions
              .sortBy(x => (x._1.x, x._1.y, x._1.z))
              .map(x => x._1.x.toString + "\t" + x._1.y + "\t" + x._1.z + "\t" + x._2)
              .mkString("\n")
          )
        case None => Left("The fellowship or template does not exist.")
      })
  }

  def setCurrentInteraction(
    fromFellowship:  String,
    fromTemplate:    String,
    fromInteraction: Int
  ): F[Either[String, String]] = {
    import cats.implicits._
    walletStateAlgebra
      .setCurrentIndices(
        fromFellowship,
        fromTemplate,
        fromInteraction
      )
      .map(_ match {
        case Some(_) => Right("Current interaction set")
        case None    => Left("Error setting current interaction")
      })
  }

  def recoverKeysFromParams(
    params: PlasmaCliParams
  ): F[Either[String, String]] = {
    import cats.implicits._
    walletAlgebra
      .recoverKeysFromParams(
        params.mnemonic.toIndexedSeq,
        params.password,
        params.network.networkId,
        NetworkConstants.MAIN_LEDGER_ID,
        params.somePassphrase,
        params.someOutputFile
      )
      .map(_ => Right("Wallet Main Key Recovered"))
  }

  def currentaddress(params: PlasmaCliParams): F[Either[String, String]] = {
    import cats.implicits._
    params.fromAddress
      .map(x => Sync[F].point(Some(x)))
      .getOrElse(
        walletStateAlgebra
          .getAddress(
            params.fromFellowship,
            params.fromTemplate,
            params.someFromInteraction
          )
      )
      .map(_ match {
        case Some(address) => Right(address)
        case None          => Left("No address found")
      })
  }

  def sync(
    networkId:  Int,
    fellowship: String,
    template:   String
  ): F[Either[String, String]] = {
    import cats.implicits._
    import TransactionBuilderApi.implicits._
    import org.plasmalabs.sdk.common.ContainsEvidence.Ops
    import org.plasmalabs.sdk.common.ContainsImmutable.instances._
    (for {
      // current indices
      someIndices <- walletStateAlgebra.getCurrentIndicesForFunds(
        fellowship,
        template,
        None
      )
      // current address
      someAddress <- walletStateAlgebra.getAddress(
        fellowship,
        template,
        someIndices.map(_.z)
      )
      // txos that are spent at current address
      txos <- someAddress
        .map(address =>
          indexerQueryAlgebra
            .queryUtxo(
              AddressCodecs.decodeAddress(address).toOption.get,
              TxoState.SPENT
            )
        )
        .getOrElse(Sync[F].pure(Seq.empty[Txo]))
    } yield
    // we have indices AND txos at current address are spent
    if (someIndices.isDefined && !txos.isEmpty) {
      // we need to update the wallet interaction with the next indices
      val indices = someIndices.map(idx => Indices(idx.x, idx.y, idx.z + 1)).get
      for {
        vks <- walletStateAlgebra.getEntityVks(
          fellowship,
          template
        )
        vksDerived <- vks.get
          .map(x =>
            walletApi.deriveChildVerificationKey(
              VerificationKey.parseFrom(
                Encoding.decodeFromBase58(x).toOption.get
              ),
              indices.z
            )
          )
          .sequence
        lock <- walletStateAlgebra.getLock(fellowship, template, indices.z)
        lockAddress = LockAddress(
          networkId,
          NetworkConstants.MAIN_LEDGER_ID,
          LockId(lock.get.getPredicate.sizedEvidence.digest.value)
        )
        _ <- walletStateAlgebra.updateWalletState(
          Encoding.encodeToBase58Check(
            lock.get.getPredicate.toByteArray
          ), // lockPredicate
          lockAddress.toBase58(), // lockAddress
          Some("ExtendedEd25519"),
          Some(Encoding.encodeToBase58(vksDerived.head.toByteArray)),
          indices
        )
      } yield txos
    } else {
      Sync[F].delay(txos)
    }).flatten
      .iterateUntil(x => x.isEmpty)
      .map { _ =>
        Right("Wallet synced")
      }
  }

  def currentaddress(
    fellowship:      String,
    template:        String,
    someInteraction: Option[Int]
  ): F[Option[String]] =
    walletStateAlgebra.getAddress(fellowship, template, someInteraction)

  def getBalance(
    someAddress:     Option[String],
    someFellowship:  Option[String],
    someTemplate:    Option[String],
    someInteraction: Option[Int]
  ): F[Either[String, String]] = {
    import cats.implicits._
    WalletModeHelper[F](
      walletStateAlgebra,
      indexerQueryAlgebra
    ).getBalance(
      someAddress,
      someFellowship,
      someTemplate,
      someInteraction
    ).map {
      _ match {
        case Left(error) => Left(error)
        case Right(balances) =>
          Right(
            (balances
              .collect { x =>
                x match {
                  case LvlBalance(b)              => "LVL: " + b
                  case GroupTokenBalanceDTO(g, b) => "Group(" + g + "): " + b
                  case SeriesTokenBalanceDTO(id, balance) =>
                    "Series(" + id + "): " + balance
                  case AssetTokenBalanceDTO(group, series, balance) =>
                    "Asset(" + group + ", " + series + "): " + balance
                }
              })
              .mkString("\n")
          )
      }
    }
  }

}
