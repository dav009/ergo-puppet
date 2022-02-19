package io.github.dav009.ergopuppet.test

import io.github.dav009.ergopuppet.Simulator._
import org.ergoplatform.appkit._
import org.ergoplatform.P2PKAddress
import org.scalatest.{ PropSpec, Matchers }
import org.ergoplatform.ErgoAddressEncoder
import org.scalatest._
import org.scalatest.{ Matchers, WordSpecLike }
import io.github.dav009.ergopuppet.model.{ TokenAmount, TokenInfo }

class MintNFTSpec extends WordSpecLike with Matchers {

  "Mint a new token" in {
    val (blockchainSim, ergoClient) = newBlockChainSimulationScenario(
      "MintNewToken")

    ergoClient.execute((ctx: BlockchainContext) => {
      val receiverParty = blockchainSim.newParty("receiver", ctx)
      receiverParty.generateUnspentBoxes(toSpend = 10000000000L)

      val spendingBoxes = ctx.getUnspentBoxesFor(receiverParty.wallet.getAddress, 0, 0)
      val txBuilder = ctx.newTxBuilder()
      val proposedToken: ErgoToken = new ErgoToken(spendingBoxes.get(0).getId, 1)
      val tokenName = "somename"
      val tokenDescription = "tokenDescription"
      val newBox = txBuilder.outBoxBuilder().mintToken(proposedToken, tokenName, tokenDescription, 10.toInt).value(MinTxFee)
        .contract(ctx.compileContract(
          ConstantsBuilder
            .create()
            .item("recPk", receiverParty.wallet.getAddress.asP2PK().pubkey)
            .build(),
          "{ recPk }")).build()

      val mintTx = ctx.newTxBuilder()
        .boxesToSpend(spendingBoxes)
        .outputs(newBox)
        .fee(Parameters.MinFee)
        .sendChangeTo(receiverParty.wallet.getAddress.getErgoAddress())
        // it should look like .sendChangeTo(prover.getP2PKAddress())
        .build()

      ctx.sendTransaction(receiverParty.wallet.sign(mintTx))

      // checking tokens and ergs
      val receiverUnspentCoins =
        blockchainSim.getUnspentCoinsFor(receiverParty.wallet.getAddress)
      assert(receiverUnspentCoins == (10000000000L - MinTxFee))

      val receiverOwnedTokens = blockchainSim.getUnspentTokensFor(receiverParty.wallet.getAddress)
      assert(receiverOwnedTokens == List(new TokenAmount(new TokenInfo(spendingBoxes.get(0).getId(), tokenName), 1)))
    })

  }

}
