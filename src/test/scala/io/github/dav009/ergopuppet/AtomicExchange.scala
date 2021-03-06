package io.github.dav009.ergopuppet.test

import io.github.dav009.ergopuppet.Simulator._
import io.github.dav009.ergopuppet.model._
import org.ergoplatform.appkit._
import org.ergoplatform.P2PKAddress
import org.scalatest.{ PropSpec, Matchers, WordSpecLike }
import org.ergoplatform.ErgoAddressEncoder
import scala.collection.JavaConverters._
import scala.collection.JavaConverters._

object AssetsAtomicExchangePlayground {

  def buyerOrder(
    ctx: BlockchainContext,
    buyerParty: Party,
    token: TokenInfo,
    tokenAmount: Long,
    ergAmount: Long,
    txFee: Long) = {

    val buyerPk = buyerParty.wallet.getAddress.getPublicKey

    val BuyerContract = """{
      sigmaProp(buyerPk || {
        (OUTPUTS.size >0 && OUTPUTS(0).R4[Coll[Byte]].isDefined) && {
          val tokens = OUTPUTS(0).tokens
          val tokenDataCorrect = tokens.size>0 &&
            tokens(0)._1 == tokenId &&
            tokens(0)._2 >= tokenAmount

          val knownId = OUTPUTS(0).R4[Coll[Byte]].get == SELF.id
          tokenDataCorrect && OUTPUTS(0).propositionBytes == buyerPk.propBytes && knownId
        }
      })
   } 
    """

    val env = ConstantsBuilder.create()
      .item("buyerPk", buyerPk)
      .item("tokenId", token.tokenId.getBytes())
      .item("tokenAmount", tokenAmount).build()

    val buyerContractCompiled = ctx.compileContract(env, BuyerContract)
    buyerContractCompiled.getErgoTree()

    val txBuilder = ctx.newTxBuilder()
    val buyerBidBox = txBuilder.
      outBoxBuilder().
      value(ergAmount)
      .contract(buyerContractCompiled).
      build()

    val boxes = ctx.getUnspentBoxesFor(buyerParty.wallet.getAddress, 0, 0)
    txBuilder
      .boxesToSpend(boxes)
      .outputs(buyerBidBox)
      .fee(txFee)
      .sendChangeTo(buyerParty.wallet.getAddress.getErgoAddress()).build()

  }

  def sellerOrder(
    ctx: BlockchainContext,
    sellerParty: Party,
    token: TokenInfo,
    tokenAmount: Long,
    ergAmount: Long,
    dexFee: Long,
    txFee: Long) = {

    val sellerPk = sellerParty.wallet.getAddress.getPublicKey

    val SellerContract = """{
      sigmaProp(sellerPk || (
        OUTPUTS.size > 1 &&
        OUTPUTS(1).R4[Coll[Byte]].isDefined
      ) && {
        val knownBoxId = OUTPUTS(1).R4[Coll[Byte]].get == SELF.id
        OUTPUTS(1).value >= ergAmount &&
        knownBoxId &&
        OUTPUTS(1).propositionBytes == sellerPk.propBytes
      })
    }
    """
    val env = ConstantsBuilder.create()
      .item("sellerPk", sellerPk)
      .item("ergAmount", ergAmount).build()

    val sellerContractCompiled = ctx.compileContract(env, SellerContract)

    val sellerBalanceBoxes = ctx.getUnspentBoxesFor(sellerParty.wallet.getAddress, 0, 0)

    val txBuilder = ctx.newTxBuilder()
    val tokens = new ErgoToken(token.tokenId, tokenAmount)

    val sellerAskBox = txBuilder.
      outBoxBuilder().
      value(dexFee)
      .contract(sellerContractCompiled)
      .tokens(tokens)
      .build()

    (
      txBuilder
      .boxesToSpend(sellerBalanceBoxes)
      .outputs(sellerAskBox)
      .fee(txFee)
      .sendChangeTo(sellerParty.wallet.getAddress.getErgoAddress()).build(),
      sellerContractCompiled)

  }

  def swapScenario = {

    val (blockchainSim, ergoClient) = newBlockChainSimulationScenario("Swap")

    println("creating token")
    val token = blockchainSim.newToken("TKN")
    println("created token")
    ergoClient.execute((ctx: BlockchainContext) => {

      val buyerParty = blockchainSim.newParty("buyer", ctx)
      val buyerBidTokenAmount = 100L
      val buyersBidNanoErgs = 50000000L
      val buyerDexFee = 1000000L
      val buyOrderTxFee = MinTxFee
      val buyerSwapBoxValue = MinErg

      buyerParty
        .generateUnspentBoxes(
          toSpend = buyersBidNanoErgs + buyOrderTxFee + buyerDexFee + buyerSwapBoxValue)

      val buyOrderTransaction =
        buyerOrder(
          ctx,
          buyerParty,
          token,
          buyerBidTokenAmount,
          buyersBidNanoErgs + buyerDexFee + buyerSwapBoxValue,
          buyOrderTxFee)

      val buyOrderTransactionSigned = buyerParty.wallet.sign(buyOrderTransaction)

      ctx.sendTransaction(buyOrderTransactionSigned)

      val sellerParty = blockchainSim.newParty("seller", ctx)
      val sellerAskNanoErgs = 50000000L
      val sellerAskTokenAmount = 100L
      val sellerDexFee = 1000000L
      val sellOrderTxFee = MinTxFee

      println("something")
      sellerParty.generateUnspentBoxes(
        toSpend = sellOrderTxFee + sellerDexFee,
        tokensToSpend = List(token -> sellerAskTokenAmount))

      val (sellOrderTransaction, _) =
        sellerOrder(
          ctx,
          sellerParty,
          token,
          sellerAskTokenAmount,
          sellerAskNanoErgs,
          sellerDexFee,
          sellOrderTxFee)

      val sellOrderTransactionSigned = sellerParty.wallet.sign(sellOrderTransaction)

      ctx.sendTransaction(sellOrderTransactionSigned)

      val txBuilder = ctx.newTxBuilder()

      val id = sellOrderTransactionSigned.getOutputsToSpend().get(0).getId().getBytes
      val c = ctx.compileContract(ConstantsBuilder.create()
        .item("pk", sellerParty.wallet.getAddress.asP2PK().pubkey).build(), "pk")
      val sellerOutBox = txBuilder
        .outBoxBuilder()
        .value(sellerAskNanoErgs)
        .registers(ErgoValue.of(id))
        .contract(c).build()

      val tokens = new ErgoToken(token.tokenId.toString(), buyerBidTokenAmount)
      val env = ConstantsBuilder.create()
        .item("pk", buyerParty.wallet.getAddress.asP2PK().pubkey).build()
      val c2 = ctx.compileContract(env, "pk")
      val id2 = buyOrderTransactionSigned.getOutputsToSpend().get(0).getId().getBytes
      val buyerOutBox = txBuilder
        .outBoxBuilder()
        .value(buyerSwapBoxValue)
        .tokens(tokens)
        .registers(ErgoValue.of(id2))
        .contract(c2).build()

      val dexParty = blockchainSim.newParty("DEX", ctx)

      val dexFee = sellerDexFee + buyerDexFee
      val swapTxFee = MinTxFee
      val c3 = ctx.compileContract(ConstantsBuilder.create()
        .item("pk", dexParty.wallet.getAddress.asP2PK().pubkey).build(), "pk")
      val dexFeeOutBox = txBuilder
        .outBoxBuilder()
        .value(dexFee - swapTxFee)
        .contract(c3).build()

      val outputsSwap = List(
        buyOrderTransactionSigned.getOutputsToSpend().get(0),
        sellOrderTransactionSigned.getOutputsToSpend().get(0)).asJava
      val swapTransaction = txBuilder
        .boxesToSpend(outputsSwap)
        .outputs(buyerOutBox, sellerOutBox, dexFeeOutBox)
        .fee(Parameters.MinFee)
        .sendChangeTo(dexParty.wallet.getAddress.getErgoAddress())
        // it should look like .sendChangeTo(prover.getP2PKAddress())
        .build()

      val swapTransactionSigned = dexParty.wallet.sign(swapTransaction)

      ctx.sendTransaction(swapTransactionSigned)

      sellerParty.printUnspentAssets()
      buyerParty.printUnspentAssets()
      dexParty.printUnspentAssets()

      // assert assets based on original implementation
      assert(blockchainSim.getUnspentCoinsFor(sellerParty.wallet.getAddress) == 50000000)
      assert(blockchainSim.getUnspentCoinsFor(buyerParty.wallet.getAddress) == 1000000)
      assert(blockchainSim.getUnspentCoinsFor(dexParty.wallet.getAddress) == 1000000)

     val sellerOwnedTokens =
        blockchainSim.getUnspentTokensFor(sellerParty.wallet.getAddress)
      assert(sellerOwnedTokens == List())

    val buyerOwnedTokens =
        blockchainSim.getUnspentTokensFor(buyerParty.wallet.getAddress)
      assert(buyerOwnedTokens == List(new TokenAmount(new TokenInfo(token.tokenId, "TKN"), 100)))

      val dexOwnedTokens = blockchainSim.getUnspentTokensFor(dexParty.wallet.getAddress)
      assert(dexOwnedTokens == List())


    })
  }

  def refundSellOrderScenario = {

    val (blockchainSim, ergoClient) = newBlockChainSimulationScenario("Refund Sell Order")

    ergoClient.execute((ctx: BlockchainContext) => {
      val token = blockchainSim.newToken("TKN")
      val sellerParty = blockchainSim.newParty("seller", ctx)
      val sellerAskNanoErgs = 50000000L
      val sellerAskTokenAmount = 100L
      val sellerDexFee = 1000000L
      val sellOrderTxFee = MinTxFee
      val cancelTxFee = MinTxFee

      sellerParty.generateUnspentBoxes(
        toSpend = sellerDexFee + sellOrderTxFee + cancelTxFee,
        tokensToSpend = List(token -> sellerAskTokenAmount))

      val (sellOrderTransaction, sellerOfferBoxContract) =
        sellerOrder(
          ctx,
          sellerParty,
          token,
          sellerAskTokenAmount,
          sellerAskNanoErgs,
          sellerDexFee,
          sellOrderTxFee)

      val sellOrderTransactionSigned = sellerParty.wallet.sign(sellOrderTransaction)

      val sellerPk = sellerParty.wallet.getAddress.getPublicKey
      val env = ConstantsBuilder.create().item("sellerPk", sellerPk).build()
      val sellerContractCompiled = ctx.compileContract(env, "{sellerPk}")
      val tx1 = ctx.sendTransaction(sellOrderTransactionSigned)
      val txBuilder = ctx.newTxBuilder()
      val sellerRefundBox = txBuilder.outBoxBuilder()
        .value(sellerDexFee)
        .tokens(new ErgoToken(token.tokenId, sellerAskTokenAmount))
        .contract(sellerContractCompiled)
        .build()

      //val inputBoxes = List(outputsSellOrder) ++

      val contractAddress = Address.fromErgoTree(sellerOfferBoxContract.getErgoTree(), NetworkType.MAINNET)
      val sellerAskBoxes = ctx.getUnspentBoxesFor(contractAddress, 0, Int.MaxValue)
      val boxs = sellerAskBoxes.asScala ++ ctx.getUnspentBoxesFor(sellerParty.wallet.getAddress, 0, Int.MaxValue).asScala
      val cancelSellTransaction = txBuilder
        .boxesToSpend(boxs.toList.asJava)
        .outputs(sellerRefundBox)
        .fee(cancelTxFee)
        .sendChangeTo(sellerParty.wallet.getAddress.getErgoAddress())
        .build()

      val cancelSellTransactionSigned = sellerParty.wallet.sign(cancelSellTransaction)

      ctx.sendTransaction(cancelSellTransactionSigned)
      sellerParty.printUnspentAssets()
        // assert assets based on original implementation
      assert(blockchainSim.getUnspentCoinsFor(sellerParty.wallet.getAddress) == 1000000)
     val sellerOwnedTokens =
        blockchainSim.getUnspentTokensFor(sellerParty.wallet.getAddress)
      assert(
        sellerOwnedTokens == List(
          new TokenAmount(new TokenInfo(token.tokenId, "TKN"), 100)
        )
      )
    })
  }
}

//refundBuyOrderScenario

import org.scalatest._

class SimplAtomicExchangeSpec extends WordSpecLike with Matchers {

  "emulate a simple transaction" in {

    AssetsAtomicExchangePlayground.swapScenario
    AssetsAtomicExchangePlayground.refundSellOrderScenario
  }
}
