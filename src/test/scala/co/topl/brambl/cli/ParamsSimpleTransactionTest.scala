package co.topl.brambl.cli
import munit.FunSuite
import scopt.OParser

import co.topl.brambl.cli.validation.BramblCliParamsValidatorModule
class ParamsSimpleTransactionTest extends FunSuite {
  import BramblCliParamsValidatorModule._

  import BramblCliParamsParserModule._

  test("Test valid transaction create") {
    val args0 = List(
      "simpletransaction",
      "create",
      "-t",
      "ptetP7jshHVrEKqDRdKAZtuybPZoMWTKKM2ngaJ7L5iZnxP5BprDB3hGJEFr",
      "-w",
      "test",
      "-o",
      "newTransaction.pbuf",
      "--genus-port",
      "9091",
      "--bifrost-port",
      "9084",
      "-h",
      "localhost",
      "-n",
      "private",
      "-a",
      "100",
      "--keyfile",
      "src/test/resources/keyfile.json",
      "--walletdb",
      "wallet.db"
    )
    val params0 = OParser.parse(paramParser, args0, BramblCliParams()).get
    assertEquals(validateParams(params0).isValid, true)
  }

  test("Test noparty transactions require index") {
    val args0 = List(
      "simpletransaction",
      "create",
      "--from-party",
      "noparty",
      "--from-contract",
      "genesis",
      "-t",
      "ptetP7jshHVrEKqDRdKAZtuybPZoMWTKKM2ngaJ7L5iZnxP5BprDB3hGJEFr",
      "-w",
      "test",
      "-o",
      "newTransaction.pbuf",
      "--genus-port",
      "9091",
      "--bifrost-port",
      "9084",
      "-h",
      "localhost",
      "-n",
      "private",
      "-a",
      "100",
      "--keyfile",
      "src/test/resources/keyfile.json",
      "--walletdb",
      "wallet.db"
    )
    val params0 = OParser.parse(paramParser, args0, BramblCliParams()).get
    assertEquals(validateParams(params0).isInvalid, true)
  }

  test("Test from-party transactions require index") {
    val args0 = List(
      "simpletransaction",
      "create",
      "--from-party",
      "noparty",
      "--from-contract",
      "genesis",
      "--from-state",
      "0",
      "-t",
      "ptetP7jshHVrEKqDRdKAZtuybPZoMWTKKM2ngaJ7L5iZnxP5BprDB3hGJEFr",
      "-w",
      "test",
      "-o",
      "newTransaction.pbuf",
      "--genus-port",
      "9091",
      "--bifrost-port",
      "9084",
      "-h",
      "localhost",
      "-n",
      "private",
      "-a",
      "100",
      "--keyfile",
      "src/test/resources/keyfile.json",
      "--walletdb",
      "wallet.db"
    )
    val params0 = OParser.parse(paramParser, args0, BramblCliParams()).get
    assertEquals(validateParams(params0).isValid, true)
  }
}