package org.plasmalabs.cli.mockbase

import org.plasmalabs.crypto.encryption.VaultStore
import org.plasmalabs.crypto.generation.mnemonic.MnemonicSize
import org.plasmalabs.quivr.models.{KeyPair, VerificationKey}
import org.plasmalabs.sdk.models.Indices
import org.plasmalabs.sdk.wallet.WalletApi

class BaseWalletApi[F[_]] extends WalletApi[F] {

  override def saveWallet(
    vaultStore: VaultStore[F],
    name:       String
  ): F[Either[WalletApi.WalletApiFailure, Unit]] = ???

  override def loadWallet(
    name: String
  ): F[Either[WalletApi.WalletApiFailure, VaultStore[F]]] = ???

  override def updateWallet(
    newWallet: VaultStore[F],
    name:      String
  ): F[Either[WalletApi.WalletApiFailure, Unit]] = ???

  override def deleteWallet(
    name: String
  ): F[Either[WalletApi.WalletApiFailure, Unit]] = ???

  override def buildMainKeyVaultStore(
    mainKey:  Array[Byte],
    password: Array[Byte]
  ): F[VaultStore[F]] = ???

  override def createNewWallet(
    password:   Array[Byte],
    passphrase: Option[String],
    mLen:       MnemonicSize
  ): F[Either[WalletApi.WalletApiFailure, WalletApi.NewWalletResult[F]]] = ???

  override def extractMainKey(
    vaultStore: VaultStore[F],
    password:   Array[Byte]
  ): F[Either[WalletApi.WalletApiFailure, KeyPair]] = ???

  override def deriveChildKeys(keyPair: KeyPair, idx: Indices): F[KeyPair] = ???

  override def deriveChildKeysPartial(
    keyPair:     KeyPair,
    xFellowship: Int,
    yTemplate:   Int
  ): F[KeyPair] = ???

  override def deriveChildVerificationKey(
    vk:  VerificationKey,
    idx: Int
  ): F[VerificationKey] = ???

  override def importWallet(
    mnemonic:   IndexedSeq[String],
    password:   Array[Byte],
    passphrase: Option[String]
  ): F[Either[WalletApi.WalletApiFailure, VaultStore[F]]] = ???

  override def saveMnemonic(
    mnemonic:     IndexedSeq[String],
    mnemonicName: String
  ): F[Either[WalletApi.WalletApiFailure, Unit]] = ???
}
