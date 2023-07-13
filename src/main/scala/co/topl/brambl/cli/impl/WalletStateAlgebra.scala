package co.topl.brambl.cli.impl

import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.implicits._
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps
import co.topl.brambl.builders.TransactionBuilderApi
import co.topl.brambl.builders.locks.LockTemplate
import co.topl.brambl.builders.locks.PropositionTemplate
import co.topl.brambl.codecs.LockTemplateCodecs.decodeLockTemplate
import co.topl.brambl.codecs.LockTemplateCodecs.encodeLockTemplate
import co.topl.brambl.dataApi.WalletStateAlgebra
import co.topl.brambl.models.Indices
import co.topl.brambl.models.box.Lock
import co.topl.brambl.utils.Encoding
import co.topl.brambl.wallet.WalletApi
import io.circe.parser._
import io.circe.syntax.EncoderOps
import quivr.models.Preimage
import quivr.models.Proposition
import quivr.models.VerificationKey

object WalletStateAlgebra {

  def make[F[_]: Sync](
      connection: Resource[F, java.sql.Connection],
      transactionBuilderApi: TransactionBuilderApi[F],
      walletApi: WalletApi[F]
  ): WalletStateAlgebra[F] =
    new WalletStateAlgebra[F] {

      override def getIndicesBySignature(
          signatureProposition: Proposition.DigitalSignature
      ): F[Option[Indices]] = connection.use { conn =>
        import cats.implicits._
        for {
          stmnt <- Sync[F].blocking(conn.createStatement())
          rs <- Sync[F].blocking(
            stmnt.executeQuery(
              s"SELECT x_party, y_contract, z_state, routine, vk FROM " +
                s"cartesian WHERE routine = '${signatureProposition.routine}' AND " +
                s"vk = '${Encoding.encodeToBase58(signatureProposition.verificationKey.toByteArray)}'"
            )
          )
          x <- Sync[F].delay(rs.getInt("x_party"))
          y <- Sync[F].delay(rs.getInt("y_contract"))
          z <- Sync[F].delay(rs.getInt("z_state"))
        } yield if (rs.next()) Some(Indices(x, y, z)) else None
      }

      def getLockByIndex(indices: Indices): F[Option[Lock.Predicate]] =
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].blocking(conn.createStatement())
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT x_party, y_contract, z_state, lock_predicate FROM " +
                  s"cartesian WHERE x_party = ${indices.x} AND " +
                  s"y_contract = ${indices.y} AND " +
                  s"z_state = ${indices.z}"
              )
            )
            _ <- Sync[F].delay(rs.next())
            lock_predicate <- Sync[F].delay(rs.getString("lock_predicate"))
          } yield Some(
            Lock.Predicate.parseFrom(
              Encoding.decodeFromBase58Check(lock_predicate).toOption.get
            )
          )
        }

      override def updateWalletState(
          lockPredicate: String,
          lockAddress: String,
          routine: Option[String],
          vk: Option[String],
          indices: Indices
      ): F[Unit] = {
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].blocking(conn.createStatement())
            statement =
              s"INSERT INTO cartesian (x_party, y_contract, z_state, lock_predicate, address, routine, vk) VALUES (${indices.x}, ${indices.y}, ${indices.z}, '${lockPredicate}', '" +
                lockAddress + "', " + routine
                  .map(x => s"'$x'")
                  .getOrElse("NULL") + ", " + vk
                  .map(x => s"'$x'")
                  .getOrElse("NULL") + ")"
            _ <- Sync[F].blocking(
              stmnt.executeUpdate(statement)
            )
          } yield ()
        }
      }

      override def getNextIndicesForFunds(
          party: String,
          contract: String
      ): F[Option[Indices]] = {
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].blocking(conn.createStatement())
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT x_party, party FROM parties WHERE party = '${party}'"
              )
            )
            x <- Sync[F].delay(rs.getInt("x_party"))
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT y_contract, contract FROM contracts WHERE contract = '${contract}'"
              )
            )
            y <- Sync[F].delay(rs.getInt("y_contract"))
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT x_party, y_contract, MAX(z_state) as z_index FROM cartesian WHERE x_party = ${x} AND y_contract = ${y}"
              )
            )
            z <- Sync[F].delay(rs.getInt("z_index"))
          } yield if (rs.next()) Some(Indices(x, y, z + 1)) else None
        }
      }

      private def validateParty(
          party: String
      ): F[ValidatedNel[String, String]] =
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].blocking(conn.createStatement())
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT x_party, party FROM parties WHERE party = '${party}'"
              )
            )
          } yield
            if (rs.next()) Validated.validNel(party)
            else Validated.invalidNel("Party not found")
        }

      private def validateContract(
          contract: String
      ): F[ValidatedNel[String, String]] =
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].blocking(conn.createStatement())
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT y_contract, contract FROM contracts WHERE contract = '${contract}'"
              )
            )
          } yield
            if (rs.next()) Validated.validNel(contract)
            else Validated.invalidNel("Contract not found")
        }

      def validateCurrentIndicesForFunds(
          party: String,
          contract: String,
          someState: Option[Int]
      ): F[ValidatedNel[String, Indices]] = {
        import cats.implicits._
        for {
          validatedParty <- validateParty(party)
          validatedContract <- validateContract(contract)
          indices <- getCurrentIndicesForFunds(party, contract, someState)
        } yield (
          validatedParty,
          validatedContract,
          indices.toValidNel("Indices not found")
        ).mapN((_, _, index) => index)
      }

      override def getAddress(
          party: String,
          contract: String,
          someState: Option[Int]
      ): F[Option[String]] = {
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].blocking(conn.createStatement())
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT x_party, party FROM parties WHERE party = '${party}'"
              )
            )
            x <- Sync[F].delay(rs.getInt("x_party"))
            query =
              s"SELECT y_contract, contract FROM contracts WHERE contract = '${contract}'"
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                query
              )
            )
            y <- Sync[F].delay(rs.getInt("y_contract"))
            query = s"SELECT address, x_party, y_contract, " + someState
              .map(_ => "z_state as z_index")
              .getOrElse(
                "MAX(z_state) as z_index"
              ) + s" FROM cartesian WHERE x_party = ${x} AND y_contract = ${y}" + someState
              .map(x => s" AND z_state = ${x}")
              .getOrElse("")
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                query
              )
            )
            address <- Sync[F].delay(rs.getString("address"))
          } yield if (rs.next()) Some(address) else None
        }
      }

      override def getCurrentIndicesForFunds(
          party: String,
          contract: String,
          someState: Option[Int]
      ): F[Option[Indices]] = {
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].blocking(conn.createStatement())
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT x_party, party FROM parties WHERE party = '${party}'"
              )
            )
            x <- Sync[F].delay(rs.getInt("x_party"))
            query =
              s"SELECT y_contract, contract FROM contracts WHERE contract = '${contract}'"
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                query
              )
            )
            y <- Sync[F].delay(rs.getInt("y_contract"))
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                s"SELECT x_party, y_contract, " + someState
                  .map(_ => "z_state as z_index")
                  .getOrElse(
                    "MAX(z_state) as z_index"
                  ) + s" FROM cartesian WHERE x_party = ${x} AND y_contract = ${y}" + someState
                  .map(x => s" AND z_state = ${x}")
                  .getOrElse("")
              )
            )
            z <- someState
              .map(x => Sync[F].point(x))
              .getOrElse(Sync[F].delay(rs.getInt("z_index")))
          } yield if (rs.next()) Some(Indices(x, y, z)) else None
        }
      }

      override def getCurrentAddress: F[String] = {
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].blocking(conn.createStatement())
            rs <- Sync[F].blocking(
              stmnt.executeQuery(
                "SELECT address, MAX(z_state) FROM cartesian WHERE x_party = 1 AND y_contract = 1  group by x_party, y_contract"
              )
            )
            lockAddress <- Sync[F].delay(rs.getString("address"))
          } yield lockAddress
        }
      }

      override def initWalletState(
          vk: VerificationKey
      ): F[Unit] = {
        import TransactionBuilderApi.implicits._
        connection.use { conn =>
          import cats.implicits._
          for {
            stmnt <- Sync[F].delay(conn.createStatement())
            _ <- Sync[F].delay(
              stmnt.execute(
                "CREATE TABLE IF NOT EXISTS cartesian (id INTEGER PRIMARY KEY," +
                  " x_party INTEGER NOT NULL, y_contract INTEGER NOT NULL, z_state INTEGER NOT NULL, " +
                  "lock_predicate TEXT NOT NULL, address TEXT NOT NULL, routine TEXT, vk TEXT)"
              )
            )
            _ <- Sync[F].delay(
              stmnt.execute(
                "CREATE TABLE IF NOT EXISTS parties (party TEXT," +
                  " x_party INTEGER PRIMARY KEY ASC)"
              )
            )
            _ <- Sync[F].delay(
              stmnt.execute(
                "CREATE TABLE IF NOT EXISTS contracts (contract TEXT NOT NULL," +
                  " y_contract INTEGER PRIMARY KEY ASC,  lock TEXT NOT NULL)"
              )
            )
            _ <- Sync[F].delay(
              stmnt.execute(
                "CREATE TABLE IF NOT EXISTS verification_keys (x_party INTEGER NOT NULL," +
                  " y_contract INTEGER NOT NULL, vks TEXT NOT NULL, PRIMARY KEY (x_party, y_contract))"
              )
            )
            _ <- Sync[F].delay(
              stmnt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS contract_names_idx ON contracts (contract)"
              )
            )
            _ <- Sync[F].delay(
              stmnt.execute(
                "CREATE INDEX IF NOT EXISTS party_names_idx ON parties (party)"
              )
            )
            _ <- Sync[F].delay(
              stmnt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS cartesian_coordinates ON cartesian (x_party, y_contract, z_state)"
              )
            )
            _ <- Sync[F].delay(
              stmnt.execute(
                "CREATE INDEX IF NOT EXISTS signature_idx ON cartesian (routine, vk)"
              )
            )
            defaultTemplate <- Sync[F].delay(
              LockTemplate.PredicateTemplate[F](
                List(
                  PropositionTemplate.SignatureTemplate[F]("ExtendedEd25519", 0)
                ),
                1
              )
            )
            genesisTemplate <- Sync[F].delay(
              LockTemplate.PredicateTemplate[F](
                List(
                  PropositionTemplate
                    .HeightTemplate[F]("header", 1, Long.MaxValue)
                ),
                1
              )
            )
            _ <- Sync[F].delay(
              stmnt.executeUpdate(
                "INSERT INTO parties (party, x_party) VALUES ('noparty', 0)"
              )
            )
            _ <- Sync[F].delay(
              stmnt.executeUpdate(
                "INSERT INTO parties (party, x_party) VALUES ('self', 1)"
              )
            )
            _ <- Sync[F].delay(
              stmnt.executeUpdate(
                s"INSERT INTO contracts (contract, y_contract, lock) VALUES ('default', 1, '${encodeLockTemplate(defaultTemplate).toString}')"
              )
            )
            _ <- Sync[F].delay(
              stmnt.executeUpdate(
                s"INSERT INTO contracts (contract, y_contract, lock) VALUES ('genesis', 2, '${encodeLockTemplate(genesisTemplate).toString}')"
              )
            )
            _ <- Sync[F].delay(
              stmnt.executeUpdate(
                // TODO: replace with proper serialization in TSDK-476
                s"INSERT INTO verification_keys (x_party, y_contract, vks) VALUES (1, 1, '${List(Encoding.encodeToBase58(vk.toByteArray)).asJson.toString}')"
              )
            )
            _ <- Sync[F].delay(
              stmnt.executeUpdate(
                s"INSERT INTO verification_keys (x_party, y_contract, vks) VALUES (0, 2, '${List[String]().asJson.toString}')"
              )
            )
            defaultSignatureLock <- getLock("self", "default", 1).map(_.get)
            signatureLockAddress <- transactionBuilderApi.lockAddress(
              defaultSignatureLock
            )
            childVk <- walletApi.deriveChildVerificationKey(vk, 1)
            genesisHeightLock <- getLock("noparty", "genesis", 1).map(_.get)
            heightLockAddress <- transactionBuilderApi.lockAddress(
              genesisHeightLock
            )
            _ <- Sync[F].delay(
              stmnt.executeUpdate(
                "INSERT INTO cartesian (x_party, y_contract, z_state, lock_predicate, address, routine, vk) VALUES (1, 1, 1, '" +
                  Encoding
                    .encodeToBase58Check(
                      defaultSignatureLock.getPredicate.toByteArray
                    ) +
                  "', '" +
                  signatureLockAddress.toBase58 + "', " + "'ExtendedEd25519', " + "'" +
                  Encoding.encodeToBase58(childVk.toByteArray)
                  + "'" + ")"
              )
            )
            _ <- Sync[F].delay(
              stmnt.executeUpdate(
                "INSERT INTO cartesian (x_party, y_contract, z_state, lock_predicate, address) VALUES (0, 2, 1, '" +
                  Encoding
                    .encodeToBase58Check(
                      genesisHeightLock.getPredicate.toByteArray
                    ) +
                  "', '" +
                  heightLockAddress.toBase58 + "')"
              )
            )
            _ <- Sync[F].delay(stmnt.close())
          } yield ()
        }
      }

      override def getPreimage(
          digestProposition: Proposition.Digest
      ): F[Option[Preimage]] =
        Sync[F].delay(
          None
        ) // We are not yet supporting Digest Propositions in brambl-cli

      override def addEntityVks(
          party: String,
          contract: String,
          entities: List[String]
      ): F[Unit] = connection.use { conn =>
        import cats.implicits._
        for {
          stmnt <- Sync[F].blocking(conn.createStatement())
          rs <- Sync[F].blocking(
            stmnt.executeQuery(
              s"SELECT x_party FROM parties WHERE party = '${party}'"
            )
          )
          x <- Sync[F].delay(rs.getInt("x_party"))
          rs <- Sync[F].blocking(
            stmnt.executeQuery(
              s"SELECT y_contract FROM contracts WHERE contract = '${contract}'"
            )
          )
          y <- Sync[F].delay(rs.getInt("y_contract"))
          statement =
            s"INSERT INTO verification_keys (x_party, y_contract, vks) VALUES (${x}, ${y}, " +
              s"'${entities.asJson.toString}')"
          _ <- Sync[F].blocking(
            stmnt.executeUpdate(statement)
          )
          // lockTempl <- getLockTemplate(contract)
          //   .map(_.get)
          // lock <- lockTempl
          //   .build(
          //     entities
          //       .map(
          //         // TODO: replace with proper serialization in TSDK-476
          //         vk =>
          //           (
          //             VerificationKey.parseFrom(
          //               Encoding.decodeFromBase58(vk).toOption.get
          //             ),
          //             vk
          //           )
          //       )
          //       .toList
          //       .map(_._1)
          //   )
          //   .map(
          //     _.getOrElse(throw new IllegalStateException("Should not happen"))
          //   )
          // lockAddress <- transactionBuilderApi.lockAddress(lock)
          // _ <- Sync[F].delay(
          //   stmnt.executeUpdate(
          //     s"INSERT INTO cartesian (x_party, y_contract, z_state, lock_predicate, address, routine, vk) VALUES ($x, $y, 1, '" +
          //       Encoding
          //         .encodeToBase58Check(
          //           lock.getPredicate.toByteArray
          //         ) +
          //       "', '" +
          //       encodeAddress(
          //         lockAddress
          //       ) + "', " + "'ExtendedEd25519', " + "''" + ")"
          //   )
          // )
        } yield ()
      }

      override def getEntityVks(
          party: String,
          contract: String
      ): F[Option[List[String]]] = connection.use { conn =>
        import cats.implicits._
        for {
          stmnt <- Sync[F].blocking(conn.createStatement())
          rs <- Sync[F].blocking(
            stmnt.executeQuery(
              s"SELECT x_party FROM parties WHERE party = '${party}'"
            )
          )
          x <- Sync[F].delay(rs.getInt("x_party"))
          rs <- Sync[F].blocking(
            stmnt.executeQuery(
              s"SELECT y_contract FROM contracts WHERE contract = '${contract}'"
            )
          )
          y <- Sync[F].delay(rs.getInt("y_contract"))
          rs <- Sync[F].blocking(
            stmnt.executeQuery(
              s"SELECT vks FROM verification_keys WHERE x_party = ${x} AND y_contract = ${y}"
            )
          )
          vks <- Sync[F].delay(rs.getString("vks"))
        } yield
          if (!rs.next()) None
          else parse(vks).toOption.flatMap(_.as[List[String]].toOption)
      }

      // FIXME: use this method to get the lock template in the controller
      override def addNewLockTemplate(
          contract: String,
          lockTemplate: LockTemplate[F]
      ): F[Unit] = connection.use { conn =>
        import cats.implicits._
        for {
          stmnt <- Sync[F].blocking(conn.createStatement())
          // FIXME: do not use max, use autoincrement
          rs <- Sync[F].blocking(
            stmnt.executeQuery(
              s"SELECT MAX(y_contract) as y_index FROM contracts"
            )
          )
          y <- Sync[F].delay(rs.getInt("y_index"))
          statement =
            s"INSERT INTO contracts (contract, y_contract, lock) VALUES ('${contract}', ${y + 1}, '${encodeLockTemplate(lockTemplate).toString}'"
          _ <- Sync[F].blocking(
            stmnt.executeUpdate(statement)
          )
        } yield ()
      }
      override def getLockTemplate(
          contract: String
      ): F[Option[LockTemplate[F]]] = connection.use { conn =>
        import cats.implicits._
        for {
          stmnt <- Sync[F].blocking(conn.createStatement())
          rs <- Sync[F].blocking(
            stmnt.executeQuery(
              s"SELECT lock FROM contracts WHERE contract = '${contract}'"
            )
          )
          lockStr <- Sync[F].delay(rs.getString("lock"))
        } yield
          if (!rs.next()) None
          else
            parse(lockStr).toOption.flatMap(decodeLockTemplate[F](_).toOption)
      }

      // Generate the next lock for a party and contract
      override def getLock(
          party: String,
          contract: String,
          nextState: Int
      ): F[Option[Lock]] = for {
        changeTemplate <- getLockTemplate(contract)
        entityVks <- getEntityVks(party, contract)
          .map(
            _.map(
              _.map(
                // TODO: replace with proper serialization in TSDK-476
                vk =>
                  VerificationKey.parseFrom(
                    Encoding.decodeFromBase58(vk).toOption.get
                  )
              )
            )
          )
        childVks <- entityVks
          .map(vks =>
            vks.map(walletApi.deriveChildVerificationKey(_, nextState)).sequence
          )
          .sequence
        changeLock <- changeTemplate
          .flatMap(template =>
            childVks.map(vks => template.build(vks).map(_.toOption))
          )
          .sequence
          .map(_.flatten)
      } yield changeLock
    }
}
