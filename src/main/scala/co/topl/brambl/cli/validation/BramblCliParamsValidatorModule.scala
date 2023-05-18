package co.topl.brambl.cli.validation

import cats.data.ValidatedNel
import co.topl.brambl.cli.BramblCliMode
import co.topl.brambl.cli.BramblCliParams
import co.topl.brambl.cli.BramblCliSubCmd
import co.topl.brambl.cli.BramblCliValidatedParams
import co.topl.brambl.cli.validation.WalletValidationModule
import co.topl.brambl.codecs.AddressCodecs

object BramblCliParamsValidatorModule
    extends CommonValidationModule
    with WalletValidationModule
    with SimpleTransactionValidationModule {

  def validateSpecificParams(
      mode: BramblCliMode.Value,
      subcmd: BramblCliSubCmd.Value,
      paramConfig: BramblCliParams
  ) = {
    (mode, subcmd) match {
      case (BramblCliMode.wallet, BramblCliSubCmd.init) =>
        validateKeyGenerationParams(paramConfig).map(_ => (mode, subcmd))
      case (BramblCliMode.simpletransaction, BramblCliSubCmd.create) =>
        validateSimpleTransactionCreateParams(paramConfig).map(_ =>
          (mode, subcmd)
        )
      case (BramblCliMode.simpletransaction, BramblCliSubCmd.prove) =>
        validateSimpleTransactionProveParams(paramConfig).map(_ =>
          (mode, subcmd)
        )
      case (BramblCliMode.simpletransaction, BramblCliSubCmd.broadcast) =>
        validateSimpleTransactionBroadcastParams(paramConfig).map(_ =>
          (mode, subcmd)
        )
      case (BramblCliMode.genusquery, BramblCliSubCmd.utxobyaddress) =>
        validateUtxoQueryParams(paramConfig).map(_ => (mode, subcmd))
    }
  }

  def validateParams(
      paramConfig: BramblCliParams
  ): ValidatedNel[String, BramblCliValidatedParams] = {
    import cats.implicits._
    (
      validateMode(paramConfig.mode)
        .andThen(mode =>
          validateSubCmd(mode, paramConfig.subcmd).map((mode, _))
        )
        .andThen(modeAndSubCmd =>
          validateSpecificParams(
            modeAndSubCmd._1,
            modeAndSubCmd._2,
            paramConfig
          )
        ),
      validateNetwork(paramConfig.network),
      validateWalletFile(paramConfig.someWalletFile),
      validateOutputfile(paramConfig.someOutputFile, required = false)
    )
      .mapN(
        (
            modeAndSubCmd,
            network,
            walletFile,
            someOutputFile
        ) => {
          BramblCliValidatedParams(
            mode = modeAndSubCmd._1,
            subcmd = modeAndSubCmd._2,
            network = network,
            password = paramConfig.password,
            walletFile = walletFile,
            toAddress = paramConfig.toAddress.map(x =>
              AddressCodecs
                .decodeAddress(x)
                .toOption
                .get
            ), // this was validated before
            fromParty = paramConfig.someFromParty.getOrElse("self"),
            fromContract = paramConfig.someFromContract.getOrElse("default"),
            someFromState = paramConfig.someFromState.map(_.toInt),
            genusPort = paramConfig.genusPort,
            host = paramConfig.host,
            amount = paramConfig.amount,
            someKeyFile = paramConfig.someKeyFile,
            somePassphrase = paramConfig.somePassphrase,
            someOutputFile = someOutputFile,
            someInputFile = paramConfig.someInputFile
          ).validNel
        }
      )
      .andThen(x => x)
  }

}
