package com.dav009.ergopilot.simulation
import com.dav009.ergopilot.model.{Wallet, Party, TokenInfo, TokenAmount}
import org.ergoplatform.{ErgoBox, ErgoAddressEncoder, P2PKAddress, ErgoLikeTransaction}
import org.ergoplatform.appkit.{
  Address,
  UnsignedTransaction,
  SignedTransaction,
  BlockchainContext,
  Mnemonic,
  InputBox
}

import org.ergoplatform.appkit.{ErgoWallet, AppkitProvingInterpreter};

import org.ergoplatform.appkit.impl.{
  SignedTransactionImpl,
  UnsignedTransactionImpl,
  BlockchainContextBase
}
import org.ergoplatform.wallet.mnemonic.{Mnemonic => WMnemonic}
import org.ergoplatform.wallet.secrets.ExtendedSecretKey
import org.ergoplatform.appkit.JavaHelpers
import java.util
import sigmastate.basics.{DLogProtocol}
import sigmastate.basics.DiffieHellmanTupleProverInput

class DummyWalletImpl(
    blockchain: DummyBlockchainSimulationImpl,
    ctx: BlockchainContextBase,
    override val name: String
) extends Wallet {

  implicit val addressEncoder = ErgoAddressEncoder(
    ErgoAddressEncoder.TestnetNetworkPrefix
  )

  private val masterKey = {
    val m    = Mnemonic.generateEnglishMnemonic()
    val seed = WMnemonic.toSeed(org.ergoplatform.wallet.interface4j.SecretString.create(m), None)
    ExtendedSecretKey.deriveMasterKey(seed)
  }

  override val getAddress: Address = new Address(P2PKAddress(masterKey.publicKey.key))

  override def getUnspentBoxes(
      amountToSpend: Long
  ): java.util.Optional[java.util.List[InputBox]] = {
    val boxes = this.ctx.getUnspentBoxesFor(this.getAddress, 0.toInt, Int.MaxValue)
    val a     = java.util.Optional.of(boxes)
    a
  }

  override def sign(tx: UnsignedTransaction): SignedTransaction = {
    val txImpl       = tx.asInstanceOf[UnsignedTransactionImpl]
    val boxesToSpend = JavaHelpers.toIndexedSeq(txImpl.getBoxesToSpend)
    val dataBoxes    = JavaHelpers.toIndexedSeq(txImpl.getDataBoxes)
    import org.ergoplatform.appkit.Iso._
    val dhtInputs = new java.util.ArrayList[DiffieHellmanTupleProverInput](0)
    val dlogs =
      JListToIndexedSeq(identityIso[ExtendedSecretKey]).from(IndexedSeq(masterKey))
    val dLogSecrets = new util.ArrayList[DLogProtocol.DLogProverInput](0)
    val prover = new AppkitProvingInterpreter(
      secretKeys = dlogs,
      dLogInputs = dLogSecrets,
      dhtInputs = dhtInputs,
      params = blockchain.parameters
    )
    val (signed, cost) =
      prover.sign(txImpl.getTx, boxesToSpend, dataBoxes, blockchain.stateContext, baseCost = 0).get

    new SignedTransactionImpl(ctx, signed, cost)
  }
}

class DummyPartyImpl(
    blockchain: DummyBlockchainSimulationImpl,
    ctx: BlockchainContext,
    override val name: String
) extends Party {

  override val wallet: Wallet =
    new DummyWalletImpl(blockchain, ctx.asInstanceOf[BlockchainContextBase], s"$name Wallet")

  override def generateUnspentBoxes(
      toSpend: Long,
      tokensToSpend: List[TokenAmount]
  ): Unit = {
    blockchain.generateUnspentBoxesFor(wallet.getAddress, toSpend, tokensToSpend)
    println(
      s"....$name: Generating unspent boxes for $toSpend nanoERGs and tokens: ${TokenAmount
        .prettyprintTokens(tokensToSpend)}"
    )
  }

  override def selectUnspentBoxes(
      toSpend: Long,
      tokensToSpend: List[TokenAmount]
  ): List[ErgoBox] =
    blockchain.selectUnspentBoxesFor(wallet.getAddress)

  override def printUnspentAssets(): Unit = {
    val coins  = blockchain.getUnspentCoinsFor(wallet.getAddress)
    val tokens = blockchain.getUnspentTokensFor(wallet.getAddress)
    println(
      s"....$name: Unspent coins: $coins nanoERGs; tokens: ${TokenAmount.prettyprintTokens(tokens)}"
    )
  }

}

object DummyPartyImpl {

  def apply(
      blockchain: DummyBlockchainSimulationImpl,
      ctx: BlockchainContext,
      name: String
  ): DummyPartyImpl =
    new DummyPartyImpl(blockchain, ctx, name)
}
