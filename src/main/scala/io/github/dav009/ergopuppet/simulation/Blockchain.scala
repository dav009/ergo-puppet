package io.github.dav009.ergopuppet.simulation
import io.github.dav009.ergopuppet.model.{BlockchainSimulation, Party, TokenAmount, TokenInfo}
import org.ergoplatform.appkit.{Iso, ErgoToken, Address, BlockchainContext, ErgoType, ErgoValue}
import org.ergoplatform.{ErgoBox, ErgoLikeTransaction}
import scala.collection.mutable
import org.ergoplatform.wallet.protocol.context.{ErgoLikeParameters, ErgoLikeStateContext}
import special.collection.Coll
import org.ergoplatform.appkit.ErgoId
import scorex.crypto.authds.ADDigest
import scorex.util.encode.Base16
import sigmastate.interpreter.CryptoConstants
import org.ergoplatform.ErgoBox.{BoxId, TokenId, AdditionalRegisters, R4}
import sigmastate.eval.Extensions.ArrayOps
import scorex.util._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{PropSpec, Matchers}
import sigmastate.serialization.generators.ObjectGenerators
import sigmastate.eval._
import collection.JavaConverters._
import sigmastate.eval.CostingDataContext
import special.sigma.{Header, PreHeader, SigmaProp, SigmaContract, Context, DslSyntaxExtensions, SigmaDslBuilder}


case class DummyBlockchainSimulationImpl(scenarioName: String)
    extends BlockchainSimulation
    with ObjectGenerators {

  private var boxes: mutable.ArrayBuffer[ErgoBox] = new mutable.ArrayBuffer[ErgoBox]()

  private var unspentBoxes: mutable.ArrayBuffer[ErgoBox] =
    new mutable.ArrayBuffer[ErgoBox]()
  private val tokenNames: mutable.Map[ErgoId, String] = mutable.Map()
  private var chainHeight: Int                        = 0
  private var nextBoxId: Short                        = 0
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
    tokensToSpend.foreach { println }
    tokensToSpend.foreach { t =>
      tokenNames += (t.token.tokenId -> t.token.tokenName)
    }

    val addTokens = tokensToSpend
      .map(ta => (ta.token.tokenId.getBytes().asInstanceOf[TokenId], ta.tokenAmount))
      .toList
    val z                = addTokens.toArray[(TokenId, Long)]
    val additionalTokens = CostingSigmaDslBuilder.Colls.fromArray(z)
    val b = new ErgoBox(
      index = nextBoxId,
      value = toSpend,
      ergoTree = address.getErgoAddress.script,
      creationHeight = chainHeight,
      transactionId = ErgoBox.allZerosModifierId,
      additionalTokens = additionalTokens
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

  def tokensToMint(tx: ErgoLikeTransaction) = {
    val tokenBoxPairs = tx.outputs.flatMap { b =>
      Iso.isoTokensListToPairsColl.from(b.additionalTokens).asScala.map { t =>
        (t, b)
      }
    }
    val newTokens = tokenBoxPairs.filter {
      case (t: ErgoToken, b: ErgoBox) =>
        !tokenNames.contains(t.getId())
    }
    val newTokensWithNames = newTokens.map {
      case (t: ErgoToken, b: ErgoBox) =>
        val s: Array[Byte] = b.getReg[Coll[Byte]](4).get.toArray
        val name           = new String(s)
        t.getId() -> name
    }
    newTokensWithNames
  }

  override def send(tx: ErgoLikeTransaction): String = {

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
    // convert from colln to List
    // add mapping from ergoId to name
    val newTokens = tokensToMint(tx)
    tokenNames ++= newTokens

    println(s"..$scenarioName: Accepting transaction ${tx.id} to the blockchain")
    tx.id
  }

  override def newToken(name: String): TokenInfo = {
    val tokenId = boxIdGen.sample.get
    tokenNames += (new ErgoId(tokenId) -> name)
    return TokenInfo(new ErgoId(tokenId), name)
  }

  def getUnspentCoinsFor(address: Address): Long =
    getUnspentBoxesFor(address).map(_.value).sum

  def getUnspentTokensFor(address: Address): List[TokenAmount] =
    getUnspentBoxesFor(address).flatMap { b =>
      b.additionalTokens.toArray.map { t =>
        TokenAmount(TokenInfo(new ErgoId(t._1), tokenNames(new ErgoId(t._1))), t._2)
      }
    }

  def getHeight: Int = chainHeight

  def setHeight(height: Int): Unit = { chainHeight = height }
}
