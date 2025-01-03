package org.plasmalabs.cli.modules

import cats.effect.IO
import org.plasmalabs.cli.controllers.FellowshipsController
import org.plasmalabs.cli.{PlasmaCliParams, PlasmaCliParamsParserModule, PlasmaCliSubCmd}
import org.plasmalabs.sdk.servicekit.{FellowshipStorageApi, WalletStateResource}
import scopt.OParser

trait FellowshipsModeModule extends WalletStateResource {

  def fellowshipsModeSubcmds(
    validateParams: PlasmaCliParams
  ): IO[Either[String, String]] = {
    val fellowshipStorageAlgebra = FellowshipStorageApi.make[IO](
      walletResource(validateParams.walletFile)
    )
    validateParams.subcmd match {
      case PlasmaCliSubCmd.invalid =>
        IO.pure(
          Left(
            OParser.usage(
              PlasmaCliParamsParserModule.fellowshipsMode
            ) + "\nA subcommand needs to be specified"
          )
        )
      case PlasmaCliSubCmd.add =>
        new FellowshipsController(fellowshipStorageAlgebra)
          .addFellowship(validateParams.fellowshipName)
      case PlasmaCliSubCmd.list =>
        new FellowshipsController(fellowshipStorageAlgebra)
          .listFellowships()
    }
  }
}
