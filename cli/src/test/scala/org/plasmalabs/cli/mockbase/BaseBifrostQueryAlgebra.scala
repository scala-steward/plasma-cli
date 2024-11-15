package org.plasmalabs.cli.mockbase

import org.plasmalabs.consensus.models.{BlockHeader, BlockId}
import org.plasmalabs.node.models.BlockBody
import org.plasmalabs.node.services.SynchronizationTraversalRes
import org.plasmalabs.sdk.dataApi.NodeQueryAlgebra
import org.plasmalabs.sdk.models.TransactionId
import org.plasmalabs.sdk.models.transaction.IoTransaction

abstract class BaseNodeQueryAlgebra[F[_]] extends NodeQueryAlgebra[F] {

  override def synchronizationTraversal(): F[Iterator[SynchronizationTraversalRes]] = ???

  override def makeBlocks(nbOfBlocks: Int): F[Unit] = ???

  override def blockByHeight(
    height: Long
  ): F[Option[(BlockId, BlockHeader, BlockBody, Seq[IoTransaction])]] = ???

  override def blockById(
    blockId: BlockId
  ): F[Option[(BlockId, BlockHeader, BlockBody, Seq[IoTransaction])]] = ???

  override def blockByDepth(
    depth: Long
  ): F[Option[(BlockId, BlockHeader, BlockBody, Seq[IoTransaction])]] = ???

  override def fetchTransaction(
    txId: TransactionId
  ): F[Option[IoTransaction]] = ???

  override def broadcastTransaction(tx: IoTransaction): F[TransactionId] = ???
}
