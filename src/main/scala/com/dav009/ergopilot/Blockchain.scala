package com.dav009.ergopilot
import org.ergoplatform.appkit.{Address, BlockchainContext}
import org.ergoplatform.{ErgoBox, ErgoLikeTransaction}
import scala.collection.mutable
import org.ergoplatform.wallet.protocol.context.{ErgoLikeParameters, ErgoLikeStateContext}
import sigmastate.eval.{CGroupElement, CPreHeader, Colls}
import special.collection.Coll
import special.sigma.{Header, PreHeader}
import scorex.crypto.authds.ADDigest
import scorex.util.ModifierId
import scorex.util.encode.Base16
import sigmastate.interpreter.CryptoConstants
import org.ergoplatform.ErgoBox.BoxId
import org.ergoplatform.ErgoBox.TokenId

import sigmastate.eval.Extensions.ArrayOps
import special.collection.Coll
import scorex.util._

//import com.dav009.ergopilot.Party
// ToDo replace this for  org.ergoplatform.explorer.client.model.{TokenInfo, TokenAmount}
case class TokenInfo(tokenId: Coll[Byte], tokenName: String)

case class TokenAmount(token: TokenInfo, tokenAmount: Long) {
  override def toString: String =
    s"(${Base16.encode(token.tokenId.toArray)}, $tokenAmount)"
}

object TokenAmount {
  implicit def apply(t: (TokenInfo, Long)): TokenAmount = new TokenAmount(t._1, t._2)

  def prettyprintTokens(tokens: List[TokenAmount]): String = {
    if (tokens.isEmpty) {
      "none"
    } else {
      tokens
        .map(t => s"${t.token.tokenName} -> ${t.tokenAmount}")
        .mkString(", ")
    }
  }

}

case class DummyBlockchainSimulationImpl(scenarioName: String) extends BlockchainSimulation {

  private var boxes: mutable.ArrayBuffer[ErgoBox] = new mutable.ArrayBuffer[ErgoBox]()

  private var unspentBoxes: mutable.ArrayBuffer[ErgoBox] =
    new mutable.ArrayBuffer[ErgoBox]()
  private val tokenNames: mutable.Map[ModifierId, String] = mutable.Map()
  private var chainHeight: Int                            = 0
  private var nextBoxId: Short                            = 0
  private def getUnspentBoxesFor(address: Address): List[ErgoBox] =
    unspentBoxes.filter { b =>
      address.getErgoAddress.script == b.ergoTree
    }.toList

  def BlockChainContext() = {}

  def stateContext: ErgoLikeStateContext = new ErgoLikeStateContext {

    override def sigmaLastHeaders: Coll[Header] = Colls.emptyColl

    override def previousStateDigest: ADDigest =
      Base16
        .decode("a5df145d41ab15a01e0cd3ffbab046f0d029e5412293072ad0f5827428589b9302")
        .map(ADDigest @@ _)
        .getOrElse(throw new Error(s"Failed to parse genesisStateDigest"))

    override def sigmaPreHeader: PreHeader = CPreHeader(
      version = 0,
      parentId = Colls.emptyColl[Byte],
      timestamp = 0,
      nBits = 0,
      height = chainHeight,
      minerPk = CGroupElement(CryptoConstants.dlogGroup.generator),
      votes = Colls.emptyColl[Byte]
    )
  }

  val parameters: ErgoLikeParameters = new ErgoLikeParameters {

    override def storageFeeFactor: Int = 1250000

    override def minValuePerByte: Int = 360

    override def maxBlockSize: Int = 524288

    override def tokenAccessCost: Int = 100

    override def inputCost: Int = 2000

    override def dataInputCost: Int = 100

    override def outputCost: Int = 100

    override def maxBlockCost: Long = 2000000

    override def softForkStartingHeight: Option[Int] = None

    override def softForkVotesCollected: Option[Int] = None

    override def blockVersion: Byte = 1
  }

  def generateUnspentBoxesFor(
      address: Address,
      toSpend: Long,
      tokensToSpend: List[TokenAmount]
  ): Unit = {

    //tokensToSpend.foreach { t =>
    //  tokenNames += (t.getTokenId.toModifierId -> t.getName())
    //}
    val b = new ErgoBox(
      index = nextBoxId,
      value = toSpend,
      ergoTree = address.getErgoAddress.script,
      creationHeight = chainHeight,
      transactionId = ErgoBox.allZerosModifierId
      // additionalTokens =
      //   tokensToSpend.map(ta => (Digest32 @@ ta.token.tokenId.toArray, ta.tokenAmount))
    )
    nextBoxId = (nextBoxId + 1).toShort
    unspentBoxes.append(b)
    boxes.append(b)
  }

  def selectUnspentBoxesFor(
      address: Address
  ): List[ErgoBox] = {
    val treeToFind = address.getErgoAddress.script
    val filtered = unspentBoxes.filter { b =>
      b.ergoTree == treeToFind
    }.toList
    filtered
  }

  def getUnspentBox(id: BoxId): ErgoBox =
    unspentBoxes.find(b => java.util.Arrays.equals(b.id, id)).get

  def getBox(id: BoxId): ErgoBox =
    boxes.find(b => java.util.Arrays.equals(b.id, id)).get

  override def newParty(name: String, ctx: BlockchainContext): Party = {
    val party = DummyPartyImpl(this, ctx, name)
    val pk    = party.wallet.getAddress
    println(s"..$scenarioName: Creating new party: $name, pk: $pk")
    party
  }

  override def send(tx: ErgoLikeTransaction): Unit = {

    val boxesToSpend   = tx.inputs.map(i => getUnspentBox(i.boxId)).toIndexedSeq
    val dataInputBoxes = tx.dataInputs.map(i => getBox(i.boxId)).toIndexedSeq
    TransactionVerifier.verify(tx, boxesToSpend, dataInputBoxes, parameters, stateContext)

    val newBoxes: mutable.ArrayBuffer[ErgoBox] = new mutable.ArrayBuffer[ErgoBox]()
    newBoxes.appendAll(tx.outputs)
    newBoxes.appendAll(
      unspentBoxes.filterNot(b => tx.inputs.map(_.boxId.toModifierId).contains(b.id.toModifierId))
    )
    unspentBoxes = newBoxes
    boxes.appendAll(tx.outputs)
    println(s"..$scenarioName: Accepting transaction ${tx.id} to the blockchain")
  }

  override def newToken(name: String): TokenInfo = {
    val tokenId = ObjectGenerators.newErgoId
    tokenNames += (tokenId.toArray.toModifierId -> name)
    TokenInfo(tokenId, name)
  }

  def getUnspentCoinsFor(address: Address): Long =
    getUnspentBoxesFor(address).map(_.value).sum

  def getUnspentTokensFor(address: Address): List[TokenAmount] =
    getUnspentBoxesFor(address).flatMap { b =>
      b.additionalTokens.toArray.map { t =>
        TokenAmount(TokenInfo(t._1.toColl, tokenNames(t._1.toModifierId)), t._2)
      }
    }

  def getHeight: Int = chainHeight

  def setHeight(height: Int): Unit = { chainHeight = height }
}

object ObjectGenerators {

  def newErgoId: Coll[Byte] =
    Array.fill(TokenId.size)((scala.util.Random.nextInt(256) - 128).toByte).toColl

}