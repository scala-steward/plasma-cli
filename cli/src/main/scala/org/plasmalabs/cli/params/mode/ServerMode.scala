package org.plasmalabs.cli.params.mode

import org.plasmalabs.cli.{PlasmaCliMode, PlasmaCliParams, PlasmaCliSubCmd}
import scopt.OParser

trait ServerMode extends Args:

  def serverMode: OParser[Unit, PlasmaCliParams] =
    builder
      .cmd("server")
      .action((_, c) => c.copy(mode = PlasmaCliMode.server))
      .text("Server mode")
      .children(
        builder
          .cmd("init")
          .action((_, c) => c.copy(subcmd = PlasmaCliSubCmd.init))
          .text("Run the server")
          .children(
            (Seq(walletDbArg.required()) ++ Seq(secureArg) ++
            keyfileAndPassword.map(_.required()) ++ hostPortNetwork.reverse.tail
              .map(_.required())): _*
          )
      )

end ServerMode
