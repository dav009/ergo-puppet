package com.dav009.ergopilot.test

import com.dav009.ergopilot.Simulator._
import org.ergoplatform.appkit._
import org.ergoplatform.P2PKAddress
import org.scalatest.flatspec.AnyFlatSpec
import org.ergoplatform.ErgoAddressEncoder

class SimpleTransactionSpec extends AnyFlatSpec {

  it should "emulate a simple transaction" in {

    val (blockchainSim, ergoClient) = newBlockChainSimulationScenario(
      "SwapWithPartialAndThenTotalMatching"
    )

    ergoClient.execute((ctx: BlockchainContext) => {
      val receiverParty = blockchainSim.newParty("receiver", ctx)
      val senderParty = blockchainSim.newParty("sender", ctx)
      senderParty.generateUnspentBoxes(toSpend = 10000000000L)

      val txBuilder = ctx.newTxBuilder()
      val amountToSpend: Long = Parameters.OneErg
      val newBox = txBuilder
        .outBoxBuilder()
        .value(amountToSpend)
        .contract(
          ctx.compileContract(
            ConstantsBuilder
              .create()
              // this looks ugly
              .item("recPk", receiverParty.wallet.getAddress.asP2PK().pubkey)
              .build(),
            "{ recPk }"
          )
        )
        .build()

      // this also works:
      //  ctx.getUnspentBoxesFor(senderParty.wallet.getAddress, 0, 0)
      val boxes = senderParty.wallet.getUnspentBoxes(amountToSpend + Parameters.MinFee).get

      val tx: UnsignedTransaction = txBuilder
        .boxesToSpend(boxes)
        .outputs(newBox)
        .fee(Parameters.MinFee)
        // it should look like .sendChangeTo(prover.getP2PKAddress())
        .sendChangeTo(P2PKAddress(senderParty.wallet.getAddress.getPublicKey()))
        .build()

      val signed = senderParty.wallet.sign(tx)
      var receiverUnspentCoins =
        blockchainSim.getUnspentCoinsFor(receiverParty.wallet.getAddress)
      assert(receiverUnspentCoins == (0))
      val txId: String = ctx.sendTransaction(signed)
      val senderPartyUnspentCoins =
        blockchainSim.getUnspentCoinsFor(senderParty.wallet.getAddress)
      receiverUnspentCoins =
        blockchainSim.getUnspentCoinsFor(receiverParty.wallet.getAddress)
      assert(
        senderPartyUnspentCoins == (10000000000L - Parameters.MinFee - amountToSpend)
      )
      assert(receiverUnspentCoins == (amountToSpend))
    })
  }
}
