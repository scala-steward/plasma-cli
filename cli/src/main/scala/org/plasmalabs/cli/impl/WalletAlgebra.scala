package org.plasmalabs.cli.impl

import cats.effect.kernel.Sync
import cats.effect.std
import org.plasmalabs.crypto.encryption.VaultStore
import org.plasmalabs.quivr.models.KeyPair
import org.plasmalabs.sdk.dataApi.WalletStateAlgebra
import org.plasmalabs.sdk.wallet.WalletApi

trait WalletAlgebra[F[_]] {

  def createWalletFromParams(
    networkId:        Int,
    ledgerId:         Int,
    password:         String,
    somePassphrase:   Option[String],
    someOutputFile:   Option[String],
    someMnemonicFile: Option[String]
  ): F[Unit]

  def recoverKeysFromParams(
    mnemonic:       IndexedSeq[String],
    password:       String,
    networkId:      Int,
    ledgerId:       Int,
    somePassphrase: Option[String],
    someOutputFile: Option[String]
  ): F[Unit]

}

object WalletAlgebra {

  def make[F[_]: Sync: std.Console](
    walletApi:      WalletApi[F],
    walletStateApi: WalletStateAlgebra[F]
  ) = new WalletAlgebra[F] {
    import cats.implicits._

    private def createNewWallet(
      password:       String,
      somePassphrase: Option[String]
    ) = walletApi
      .createNewWallet(
        password.getBytes(),
        somePassphrase
      )
      .map(_.fold(throw _, identity))

    private def recoverWalletKey(
      mnemonic:       IndexedSeq[String],
      password:       String,
      somePassphrase: Option[String]
    ) = walletApi
      .importWallet(
        mnemonic,
        password.getBytes(),
        somePassphrase
      )
      .map(_.fold(throw _, identity))

    private def extractMainKey(
      wallet:   VaultStore[F],
      password: String
    ): F[KeyPair] =
      walletApi
        .extractMainKey(
          wallet,
          password.getBytes()
        )
        .flatMap(
          _.fold(
            _ =>
              Sync[F].raiseError[KeyPair](
                new Throwable("No input file (should not happen)")
              ),
            Sync[F].point(_)
          )
        )

    private def saveWallet(
      wallet:     VaultStore[F],
      outputFile: String
    ) =
      walletApi
        .saveWallet(
          wallet,
          outputFile
        )
        .map(_.fold(throw _, identity))

    private def saveMnemonic(
      mnemonic:     IndexedSeq[String],
      mnemonicFile: String
    ) =
      walletApi
        .saveMnemonic(
          mnemonic,
          mnemonicFile
        )
        .map(_.fold(throw _, identity))

    def createWalletFromParams(
      networkId:        Int,
      ledgerId:         Int,
      password:         String,
      somePassphrase:   Option[String],
      someOutputFile:   Option[String],
      someMnemonicFile: Option[String]
    ) = {
      import io.circe.syntax._
      import org.plasmalabs.crypto.encryption.VaultStore.Codecs._

      for {
        wallet  <- createNewWallet(password, somePassphrase)
        keyPair <- extractMainKey(wallet.mainKeyVaultStore, password)
        _ <- someOutputFile
          .map { outputFile =>
            saveWallet(
              wallet.mainKeyVaultStore,
              outputFile
            )
          }
          .getOrElse {
            std
              .Console[F]
              .println(new String(wallet.mainKeyVaultStore.asJson.noSpaces))
          }
        _ <- someMnemonicFile
          .map { mnemonicFile =>
            saveMnemonic(
              wallet.mnemonic,
              mnemonicFile
            )
          }
          .getOrElse {
            std.Console[F].println(wallet.mnemonic.mkString(","))
          }
        _ <- walletStateApi.initWalletState(networkId, ledgerId, keyPair)
      } yield ()

    }
    def recoverKeysFromParams(
      mnemonic:       IndexedSeq[String],
      password:       String,
      networkId:      Int,
      ledgerId:       Int,
      somePassphrase: Option[String],
      someOutputFile: Option[String]
    ) = {
      import io.circe.syntax._
      import org.plasmalabs.crypto.encryption.VaultStore.Codecs._

      for {
        wallet  <- recoverWalletKey(mnemonic, password, somePassphrase)
        keyPair <- extractMainKey(wallet, password)
        _       <- walletStateApi.initWalletState(networkId, ledgerId, keyPair)
        _ <- someOutputFile
          .map { outputFile =>
            saveWallet(
              wallet,
              outputFile
            )
          }
          .getOrElse {
            std.Console[F].println(new String(wallet.asJson.noSpaces))
          }
      } yield ()
    }
  }

}
