package com.dav009.ergopilot.simulation

import org.ergoplatform.ErgoLikeTransaction
import org.ergoplatform.wallet.interpreter.ErgoInterpreter
import org.ergoplatform.wallet.protocol.context.ErgoLikeParameters
import org.ergoplatform.wallet.protocol.context.TransactionContext
import org.ergoplatform.ErgoLikeContext
import org.ergoplatform.wallet.protocol.context.ErgoLikeStateContext
import special.collection.Coll
import special.sigma.Header
import special.sigma.PreHeader
import sigmastate.AvlTreeData
import sigmastate.eval._
import special.sigma.GroupElement
import sigmastate.interpreter.CryptoConstants
import org.ergoplatform.ErgoBox

object TransactionVerifier {

  val MaxBlockCostDefault: Int = 2000000

  val validationSettings =
    org.ergoplatform.validation.ValidationRules.currentSettings

  def verify(
      tx: ErgoLikeTransaction,
      boxesToSpend: IndexedSeq[ErgoBox],
      dataInputBoxes: IndexedSeq[ErgoBox],
      params: ErgoLikeParameters,
      stateContext: ErgoLikeStateContext
  ): Unit = {
    val verifier = ErgoInterpreter(params)
    boxesToSpend.zipWithIndex.foreach {
      case (box, idx) =>
        val input           = tx.inputs(idx)
        val proof           = input.spendingProof
        val proverExtension = proof.extension
        val ctx = new ErgoLikeContext(
          lastBlockUtxoRoot = ErgoInterpreter.avlTreeFromDigest(stateContext.previousStateDigest),
          headers = stateContext.sigmaLastHeaders,
          preHeader = stateContext.sigmaPreHeader,
          dataBoxes = dataInputBoxes,
          boxesToSpend = boxesToSpend,
          spendingTransaction = tx,
          selfIndex = idx,
          extension = proverExtension,
          validationSettings = validationSettings,
          costLimit = MaxBlockCostDefault,
          initCost = 0,
          activatedScriptVersion = 0.toByte //(stateContext.blockVersion - 1).toByte
        )

        val res = verifier.verify(box.ergoTree, ctx, proof, tx.messageToSign).get
        require(res._1, s"box (index $idx) verification failed (cost ${res._2})")
    }
  }
}
