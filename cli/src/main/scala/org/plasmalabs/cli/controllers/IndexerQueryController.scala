package org.plasmalabs.cli.controllers

import cats.Monad
import cats.effect.kernel.Sync
import org.plasmalabs.cli.TokenType
import org.plasmalabs.sdk.codecs.AddressCodecs
import org.plasmalabs.sdk.dataApi.{IndexerQueryAlgebra, WalletStateAlgebra}
import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps

class IndexerQueryController[F[_]: Sync](
  walletStateAlgebra:  WalletStateAlgebra[F],
  indexerQueryAlgebra: IndexerQueryAlgebra[F]
) {

  def queryUtxoFromParams(
    someFromAddress:     Option[String],
    fromFellowship:      String,
    fromTemplate:        String,
    someFromInteraction: Option[Int],
    tokenType:           TokenType.Value = TokenType.all
  ): F[Either[String, String]] = {

    import cats.implicits._
    someFromAddress
      .map(x => Sync[F].point(Some(x)))
      .getOrElse(
        walletStateAlgebra
          .getAddress(fromFellowship, fromTemplate, someFromInteraction)
      )
      .flatMap {
        case Some(address) =>
          indexerQueryAlgebra
            .queryUtxo(AddressCodecs.decodeAddress(address).toOption.get)
            .map(_.filter { x =>
              import monocle.macros.syntax.lens._
              val lens = x.focus(_.transactionOutput.value.value)
              if (tokenType == TokenType.lvl)
                lens.get.isLvl
              else if (tokenType == TokenType.topl)
                lens.get.isTopl
              else if (tokenType == TokenType.asset)
                lens.get.isAsset
              else if (tokenType == TokenType.series)
                lens.get.isSeries
              else if (tokenType == TokenType.group)
                lens.get.isGroup
              else
                true
            })
            .map { txos =>
              if (txos.isEmpty) Left("No UTXO found")
              else
                Right(txos.map(_.display).mkString("\n\n"))
            }
            .attempt
            .map {
              _ match {
                case Left(_)     => Left("Problem contacting the network.")
                case Right(txos) => txos
              }
            }
        case None => Monad[F].pure(Left("Address not found"))
      }

  }
}
