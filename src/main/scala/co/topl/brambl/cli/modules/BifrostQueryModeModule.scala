package co.topl.brambl.cli.modules

import cats.effect.IO
import co.topl.brambl.cli.BramblCliValidatedParams
import co.topl.brambl.cli.controllers.BifrostQueryController
import co.topl.brambl.cli.modules.ChannelResourceModule
import co.topl.brambl.cli.BramblCliSubCmd
import co.topl.brambl.dataApi.BifrostQueryAlgebra

trait BifrostQueryModeModule extends ChannelResourceModule {

  def bifrostQuerySubcmd(
      validateParams: BramblCliValidatedParams
  ): IO[Either[String, String]] = {
    val bifrostQueryAlgebra = BifrostQueryAlgebra.make[IO](
      channelResource(
        validateParams.host,
        validateParams.bifrostPort
      )
    )
    validateParams.subcmd match {
      case BramblCliSubCmd.blockbyheight =>
        new BifrostQueryController(
          bifrostQueryAlgebra
        ).blockByHeight(validateParams.height)
      case BramblCliSubCmd.blockbyid =>
        new BifrostQueryController(
          bifrostQueryAlgebra
        ).blockById(validateParams.blockId.get)
      case BramblCliSubCmd.transactionbyid =>
        new BifrostQueryController(
          bifrostQueryAlgebra
        ).fetchTransaction(validateParams.transactionId.get)
    }
  }

}