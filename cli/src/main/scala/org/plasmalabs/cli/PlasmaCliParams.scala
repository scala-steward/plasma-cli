package org.plasmalabs.cli

import org.plasmalabs.sdk.constants.NetworkConstants
import org.plasmalabs.sdk.models.{GroupId, LockAddress, SeriesId}

import java.io.File
import scala.collection.immutable.IndexedSeq

object PlasmaCliMode extends Enumeration {
  type PlasmaCliMode = Value

  val invalid, wallet, indexerquery, nodequery, simpletransaction, simpleminting, fellowships, templates, tx, server,
    help =
    Value
}

object PlasmaCliSubCmd extends Enumeration {
  type StrataCliSubCmd = Value

  val invalid, init, recoverkeys, utxobyaddress, blockbyheight, blockbyid, transactionbyid, create, prove, broadcast,
    currentaddress, list, add, inspect, exportvk, addsecret, getpreimage, importvks, sync, setinteraction,
    listinteraction, balance, mintblock = Value
}

sealed abstract class NetworkIdentifiers(
  val i:         Int,
  val name:      String,
  val networkId: Int
) {
  override def toString: String = name
}

case object NetworkIdentifiers {

  def values = Set(Mainnet, Testnet, Privatenet)

  def fromString(s: String): Option[NetworkIdentifiers] =
    s match {
      case "mainnet" => Some(Mainnet)
      case "testnet" => Some(Testnet)
      case "private" => Some(Privatenet)
      case _         => None
    }
}

case object Mainnet extends NetworkIdentifiers(0, "mainnet", NetworkConstants.MAIN_NETWORK_ID)
case object Testnet extends NetworkIdentifiers(1, "testnet", NetworkConstants.TEST_NETWORK_ID)
case object Privatenet extends NetworkIdentifiers(2, "private", NetworkConstants.PRIVATE_NETWORK_ID)
case object InvalidNet extends NetworkIdentifiers(-1, "invalid", NetworkConstants.PRIVATE_NETWORK_ID)

object TokenType extends Enumeration {
  type TokenType = Value

  val all, lvl, topl, asset, group, series = Value
}

object DigestType {

  def withName(name: String): DigestType =
    name match {
      case "sha256"  => Sha256
      case "blake2b" => Blake2b
      case _         => InvalidDigest
    }
}

sealed abstract class DigestType(
  val shortName:        String,
  val digestIdentifier: String
)

case object Sha256 extends DigestType("sha256", "Sha256")
case object Blake2b extends DigestType("blake2b", "Blake2b256")
case object InvalidDigest extends DigestType("invalid", "Invalid")

final case class PlasmaCliParams(
  mode:                  PlasmaCliMode.Value = PlasmaCliMode.invalid,
  subcmd:                PlasmaCliSubCmd.Value = PlasmaCliSubCmd.invalid,
  tokenType:             TokenType.Value = TokenType.all,
  network:               NetworkIdentifiers = InvalidNet,
  secret:                String = "",
  digestText:            String = "",
  digest:                DigestType = InvalidDigest,
  fellowshipName:        String = "self",
  templateName:          String = "default",
  lockTemplate:          String = "",
  inputVks:              Seq[File] = Seq(),
  host:                  String = "",
  nodePort:              Int = 0,
  walletFile:            String = "",
  password:              String = "",
  fromFellowship:        String = "self",
  fromTemplate:          String = "default",
  fromAddress:           Option[String] = None,
  nbOfBlocks:            Int = -1,
  height:                Long = -1,
  blockId:               String = "",
  transactionId:         String = "",
  someFromInteraction:   Option[Int] = None,
  someChangeFellowship:  Option[String] = None,
  someChangeTemplate:    Option[String] = None,
  someChangeInteraction: Option[Int] = None,
  toAddress:             Option[LockAddress] = None,
  someToFellowship:      Option[String] = None,
  someToTemplate:        Option[String] = None,
  amount:                Long = -1,
  fee:                   Long = -1,
  somePassphrase:        Option[String] = None,
  someKeyFile:           Option[String] = None,
  someInputFile:         Option[String] = None,
  someCommitment:        Option[String] = None,
  ephemeralMetadata:     Option[File] = None,
  someOutputFile:        Option[String] = None,
  mnemonic:              Seq[String] = IndexedSeq(),
  someMnemonicFile:      Option[String] = None,
  somePolicyFile:        Option[File] = None,
  someGroupId:           Option[GroupId] = None,
  someSeriesId:          Option[SeriesId] = None,
  secureConnection:      Boolean = false
)
