package org.plasmalabs.cli.controllers

import cats.effect.kernel.{Resource, Sync}
import com.google.protobuf.ByteString
import org.plasmalabs.cli.impl.{
  AssetMintingStatementParser,
  CreateTxError,
  GroupPolicyParser,
  SeriesPolicyParser,
  SimpleMintingAlgebra,
  SimpleTransactionAlgebraError
}
import org.plasmalabs.sdk.utils.Encoding

import java.io.File

class SimpleMintingController[F[_]: Sync](
  groupPolicyParserAlgebra:           GroupPolicyParser[F],
  seriesPolicyParserAlgebra:          SeriesPolicyParser[F],
  assetMintingStatementParserAlgebra: AssetMintingStatementParser[F],
  simpleMintingOps:                   SimpleMintingAlgebra[F]
) {

  import cats.implicits._

  def createSimpleGroupMintingTransactionFromParams(
    inputFile:           String,
    keyFile:             String,
    password:            String,
    fromFellowship:      String,
    fromTemplate:        String,
    someFromInteraction: Option[Int],
    amount:              Long,
    fee:                 Long,
    outputFile:          String
  ): F[Either[String, String]] =
    (for {
      gp <- groupPolicyParserAlgebra
        .parseGroupPolicy(
          Resource.make(
            Sync[F].delay(scala.io.Source.fromFile(inputFile))
          )(source => Sync[F].delay(source.close()))
        )
        .map(
          _.leftMap(e => CreateTxError("Error parsing group policy: " + e.description))
        )
      policy <- Sync[F].fromEither(gp)
      _ <- simpleMintingOps
        .createSimpleGroupMintingTransactionFromParams(
          keyFile,
          password,
          fromFellowship,
          fromTemplate,
          someFromInteraction,
          amount,
          fee,
          outputFile,
          policy
        )
    } yield ()).attempt
      .map(_ match {
        case Right(_) => Right("Transaction successfully created")
        case Left(value: SimpleTransactionAlgebraError) =>
          Left(value.description)
        case Left(e) => Left(e.toString())
      })

  def createSimpleSeriesMintingTransactionFromParams(
    inputFile:           String,
    keyFile:             String,
    password:            String,
    fromFellowship:      String,
    fromTemplate:        String,
    someFromInteraction: Option[Int],
    amount:              Long,
    fee:                 Long,
    outputFile:          String
  ): F[Either[String, String]] =
    (for {
      gp <- seriesPolicyParserAlgebra
        .parseSeriesPolicy(
          Resource.make(
            Sync[F].delay(scala.io.Source.fromFile(inputFile))
          )(source => Sync[F].delay(source.close()))
        )
        .map(
          _.leftMap(e => CreateTxError("Error parsing series policy: " + e.description))
        )
      policy <- Sync[F].fromEither(gp)
      _ <- simpleMintingOps
        .createSimpleSeriesMintingTransactionFromParams(
          keyFile,
          password,
          fromFellowship,
          fromTemplate,
          someFromInteraction,
          amount,
          fee,
          outputFile,
          policy
        )
    } yield ()).attempt
      .map(_ match {
        case Right(_) => Right("Transaction successfully created")
        case Left(value: SimpleTransactionAlgebraError) =>
          Left(value.description)
        case Left(e) => Left(e.toString())
      })

  private def loadJsonMetadata(someEphemeralMetadata: Option[File]) =
    (someEphemeralMetadata.map { ephemeralMetadata =>
      val inputRes = Resource.make(
        Sync[F]
          .delay(
            scala.io.Source.fromFile(ephemeralMetadata)
          )
      )(source => Sync[F].delay(source.close()))
      for {
        jsonInput <- inputRes.use(source => Sync[F].delay(source.getLines().mkString))
        json <- Sync[F].fromEither {
          import io.circe.parser.parse
          parse(jsonInput).leftMap(e => CreateTxError(e.toString()))
        }
      } yield json
    }).sequence

  def createSimpleAssetMintingTransactionFromParams(
    inputFile:           String,
    keyFile:             String,
    password:            String,
    fromFellowship:      String,
    fromTemplate:        String,
    someFromInteraction: Option[Int],
    fee:                 Long,
    ephemeralMetadata:   Option[File],
    someCommitment:      Option[String],
    outputFile:          String
  ): F[Either[String, String]] =
    (for {
      ams <- assetMintingStatementParserAlgebra
        .parseAssetMintingStatement(
          Resource.make(
            Sync[F].delay(scala.io.Source.fromFile(inputFile))
          )(source => Sync[F].delay(source.close()))
        )
        .map(
          _.leftMap(e =>
            CreateTxError(
              "Error parsing asset minting statement: " + e.description
            )
          )
        )
      json <- loadJsonMetadata(ephemeralMetadata)
      eitherCommitment = someCommitment.map(x => Sync[F].fromEither(Encoding.decodeFromHex(x)))
      commitment <- eitherCommitment.map(Some(_)).getOrElse(None).sequence
      statement  <- Sync[F].fromEither(ams)
      _ <- simpleMintingOps
        .createSimpleAssetMintingTransactionFromParams(
          keyFile,
          password,
          fromFellowship,
          fromTemplate,
          someFromInteraction,
          fee,
          outputFile,
          json,
          commitment.map(x => ByteString.copyFrom(x)),
          statement
        )
    } yield ()).attempt
      .map(_ match {
        case Right(_) => Right("Transaction successfully created")
        case Left(value: SimpleTransactionAlgebraError) =>
          Left(value.description)
        case Left(e) => Left(e.toString())
      })

}
