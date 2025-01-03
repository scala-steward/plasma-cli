package org.plasmalabs.cli

import munit.FunSuite
import scopt.OParser

import java.io.File
import java.nio.file.{Files, Path, Paths}

class ParamsTemplatesModuleTest extends FunSuite {

  import PlasmaCliParamsParserModule._

  val tmpWallet = FunFixture[Path](
    setup = { _ =>
      val file = new File("wallet.db")
      file.createNewFile();
      Paths.get(file.getAbsolutePath().toString())
    },
    teardown = { initialWalletDb =>
      if (Files.exists(initialWalletDb))
        Files.delete(initialWalletDb)
    }
  )

  tmpWallet.test("Test templates add (happy path)") { _ =>
    val args0 = List(
      "templates",
      "add",
      "--walletdb",
      "wallet.db",
      "--template-name",
      "test",
      "--lock-template",
      "threshold(1, sign(0) and digest(b39f7e1305cd9107ed9af824fcb0729ce9888bbb7f219cc0b6731332105675dc))"
    )
    assert(
      OParser
        .parse(paramParser, args0, PlasmaCliParams())
        .isDefined
    )
  }

  tmpWallet.test("Test templates add invalid(TSDK-760)") { _ =>
    val args0 = List(
      "templates",
      "add",
      "--walletdb",
      "wallet.db"
    )
    assert(
      OParser
        .parse(paramParser, args0, PlasmaCliParams())
        .isEmpty
    )
  }

}
