package org.plasmalabs.cli.controllers

import cats.Monad
import cats.data.ValidatedNel
import cats.effect.IO
import com.google.protobuf.ByteString
import munit.CatsEffectSuite
import org.plasmalabs.cli.TokenType
import org.plasmalabs.cli.impl.{
  AssetStatementParserModule,
  GroupPolicyParserModule,
  SeriesPolicyParserModule,
  SimpleTransactionAlgebra
}
import org.plasmalabs.cli.mockbase.BaseWalletStateAlgebra
import org.plasmalabs.cli.modules.{DummyObjects, SimpleMintingAlgebraModule}
import org.plasmalabs.sdk.codecs.AddressCodecs
import org.plasmalabs.sdk.constants.NetworkConstants
import org.plasmalabs.sdk.models.box.Lock
import org.plasmalabs.sdk.models.{GroupId, Indices, SeriesId}
import org.plasmalabs.sdk.utils.Encoding

class SimpleTransactionControllerSpec
    extends CatsEffectSuite
    with GroupPolicyParserModule
    with SeriesPolicyParserModule
    with AssetStatementParserModule
    with SimpleMintingAlgebraModule
    with DummyObjects {

  def makeWalletStateAlgebraMockWithAddress[F[_]: Monad] =
    new BaseWalletStateAlgebra[F] {

      override def getAddress(
        fellowship:  String,
        template:    String,
        interaction: Option[Int]
      ): F[Option[String]] =
        Monad[F].pure(
          Some(
            AddressCodecs.encodeAddress(
              transactionBuilderApi(
                NetworkConstants.PRIVATE_NETWORK_ID,
                NetworkConstants.MAIN_LEDGER_ID
              ).lockAddress(
                Lock().withPredicate(
                  lock01
                )
              ).unsafeRunSync()
            )
          )
        )

      override def getCurrentIndicesForFunds(
        fellowship:  String,
        template:    String,
        interaction: Option[Int]
      ): F[Option[Indices]] = Monad[F].pure(
        Some(Indices(1, 1, 1))
      )

      override def getNextIndicesForFunds(
        fellowship: String,
        template:   String
      ): F[Option[Indices]] = Monad[F].pure(
        Some(Indices(1, 1, 1))
      )

      override def updateWalletState(
        lockPredicate: String,
        lockAddress:   String,
        routine:       Option[String],
        vk:            Option[String],
        indices:       Indices
      ): F[Unit] = Monad[F].pure(())

      override def getLock(
        fellowship: String,
        template:   String,
        nextState:  Int
      ): F[Option[Lock]] =
        Monad[F].pure(
          Some(
            Lock().withPredicate(
              lock01
            )
          )
        )

      override def getLockByIndex(indices: Indices): F[Option[Lock.Predicate]] =
        Monad[F].pure(
          Some(
            lock01
          )
        )

      override def validateCurrentIndicesForFunds(
        fellowship:      String,
        template:        String,
        someInteraction: Option[Int]
      ): F[ValidatedNel[String, Indices]] = {
        import cats.implicits._
        Indices(1, 2, 3).validNel.pure[F]
      }

    }

  def simplTransactionAlgebra() = SimpleTransactionAlgebra.make[IO](
    walletApi,
    makeWalletStateAlgebraMockWithAddress[IO],
    makeIndexerQueryAlgebraMockWithAddress,
    transactionBuilderApi(
      NetworkConstants.PRIVATE_NETWORK_ID,
      NetworkConstants.MAIN_LEDGER_ID
    ),
    walletManagementUtils
  )

  val controllerUnderTest = new SimpleTransactionController(
    makeWalletStateAlgebraMockWithAddress[IO],
    simplTransactionAlgebra()
  )

  test(
    "createSimpleTransactionFromParams should create a lvl transfer transaction (no change)"
  ) {
    assertIO(
      controllerUnderTest.createSimpleTransactionFromParams(
        "src/test/resources/keyfile.json",
        "test",
        ("self", "default", None),
        (None, None, None),
        None,
        Some("self"),
        Some("default"),
        1L,
        10,
        "target/transaction.pbuf",
        TokenType.lvl,
        None,
        None
      ),
      Right("Transaction successfully created")
    )
  }

  test(
    "createSimpleTransactionFromParams should create a lvl transfer transaction (with change)"
  ) {
    assertIO(
      controllerUnderTest.createSimpleTransactionFromParams(
        "src/test/resources/keyfile.json",
        "test",
        ("self", "default", None),
        (Some("nofellowship"), Some("genesis"), None),
        None,
        Some("self"),
        Some("default"),
        1L,
        10,
        "target/transaction.pbuf",
        TokenType.lvl,
        None,
        None
      ),
      Right("Transaction successfully created")
    )
  }

  test(
    "createSimpleTransactionFromParams should create a group transfer transaction"
  ) {
    assertIO(
      controllerUnderTest.createSimpleTransactionFromParams(
        "src/test/resources/keyfile.json",
        "test",
        ("self", "default", None),
        (None, None, None),
        None,
        Some("self"),
        Some("default"),
        1L,
        10,
        "target/transaction.pbuf",
        TokenType.group,
        Some(
          GroupId(
            ByteString.copyFrom(
              Encoding
                .decodeFromHex(
                  "fdae7b6ea08b7d5489c3573abba8b1765d39365b4e803c4c1af6b97cf02c54bf"
                )
                .toOption
                .get
            )
          )
        ),
        None
      ),
      Right("Transaction successfully created")
    )
  }

  test(
    "createSimpleTransactionFromParams should create a series transfer transaction"
  ) {
    assertIO(
      controllerUnderTest.createSimpleTransactionFromParams(
        "src/test/resources/keyfile.json",
        "test",
        ("self", "default", None),
        (None, None, None),
        None,
        Some("self"),
        Some("default"),
        1L,
        10,
        "target/transaction.pbuf",
        TokenType.series,
        None,
        Some(
          SeriesId(
            ByteString.copyFrom(
              Encoding
                .decodeFromHex(
                  "1ed1caaefda61528936051929c525a17a0d43ea6ae09592da06c9735d9416c03"
                )
                .toOption
                .get
            )
          )
        )
      ),
      Right("Transaction successfully created")
    )
  }

  test(
    "createSimpleTransactionFromParams should create an asset transfer transaction"
  ) {
    assertIO(
      controllerUnderTest.createSimpleTransactionFromParams(
        "src/test/resources/keyfile.json",
        "test",
        ("self", "default", None),
        (None, None, None),
        None,
        Some("self"),
        Some("default"),
        1L,
        10,
        "target/transaction.pbuf",
        TokenType.asset,
        Some(
          GroupId(
            ByteString.copyFrom(
              Encoding
                .decodeFromHex(
                  "fdae7b6ea08b7d5489c3573abba8b1765d39365b4e803c4c1af6b97cf02c54bf"
                )
                .toOption
                .get
            )
          )
        ),
        Some(
          SeriesId(
            ByteString.copyFrom(
              Encoding
                .decodeFromHex(
                  "1ed1caaefda61528936051929c525a17a0d43ea6ae09592da06c9735d9416c03"
                )
                .toOption
                .get
            )
          )
        )
      ),
      Right("Transaction successfully created")
    )
  }

}
