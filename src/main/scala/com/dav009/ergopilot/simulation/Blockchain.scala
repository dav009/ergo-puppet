package com.dav009.ergopilot.simulation
import com.dav009.ergopilot.model.{BlockchainSimulation, Party, TokenAmount, TokenInfo}
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoType}
import org.ergoplatform.{ErgoBox, ErgoLikeTransaction}
import scala.collection.mutable
import org.ergoplatform.wallet.protocol.context.{ErgoLikeParameters, ErgoLikeStateContext}
import sigmastate.eval.{CGroupElement, CPreHeader, Colls}
import special.collection.Coll
import org.ergoplatform.appkit.ErgoId
import special.sigma.{Header, PreHeader}
import scorex.crypto.authds.ADDigest
import scorex.util.ModifierId
import scorex.util.encode.Base16
import sigmastate.interpreter.CryptoConstants
import org.ergoplatform.ErgoBox.BoxId
import org.ergoplatform.ErgoBox.TokenId
import org.ergoplatform.ErgoBox.{AdditionalRegisters, R4, TokenId}
import sigmastate.eval.Extensions.ArrayOps
import special.collection.Coll
import scorex.util._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{PropSpec, Matchers}
import org.scalacheck.Arbitrary._
import sigmastate.serialization.generators.ObjectGenerators
import sigmastate.eval.Extensions._
import sigmastate.eval._
import scorex.crypto.hash.Digest32
//import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.{Iso, _}
import sigmastate.utils.Helpers._
import collection.JavaConverters._
import sigmastate.eval.CostingDataContext

import scalan.RType

import org.ergoplatform.ErgoBox.{AdditionalRegisters, R4, TokenId}

case class DummyBlockchainSimulationImpl(scenarioName: String) extends BlockchainSimulation with ObjectGenerators{

  private var boxes: mutable.ArrayBuffer[ErgoBox] = new mutable.ArrayBuffer[ErgoBox]()

  private var unspentBoxes: mutable.ArrayBuffer[ErgoBox] =
    new mutable.ArrayBuffer[ErgoBox]()
  private val tokenNames: mutable.Map[ErgoId, String] = mutable.Map()
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

    println("tospend")
    println(tokensToSpend)
    tokensToSpend.foreach{println}
    tokensToSpend.foreach { t =>
      tokenNames += (t.token.tokenId -> t.token.tokenName)
    }

    import sigmastate.eval.CostingSigmaDslBuilder
    import RType._
    import special.collection.Coll
    import special.sigma.{SigmaProp, SigmaContract, Context, DslSyntaxExtensions, SigmaDslBuilder}

    val addTokens = tokensToSpend.map(ta => (ta.token.tokenId.getBytes().asInstanceOf[TokenId], ta.tokenAmount)).toList
    //val newToknes =  tokensToSpend.map(ta => new ErgoToken(ta.token.tokenId, ta.tokenAmount))
    //isoTokensListToPairsColl
    //implicit val TokenIdRType2: RType[TokenId] = RType.arrayRType[Byte].asInstanceOf[RType[TokenId]]
    //implicit val pairType: RType[(TokenId, Long)] = RType.pairRType[ RType[TokenId], RType[Long]].asInstanceOf[RType[(TokenId, Long)]]

    //implicit val pair: RType[TokenId] = RType.pairRType(TokenIdRType, RType.LongType)
    println("before z")
    val z= addTokens.toArray[(TokenId, Long)]
    println("after z")
    //val ergoType = 
    //  ErgoType.pairType(
     //   new ErgoType(TokenIdRType),
      //  ErgoType.longType())
    //val innerErgoType = ErgoType.byteType()
    //val t =  ergoTyepe.getRType()
    println("Z::")
    println(z)
    z.foreach(println)
    println(z.size)
    println("converting to coll")
    
    if (z.size > 0){
      val ccc = List((z(0)._1, z(0)._2)).toArray
      println("AAAAA")
      val aaa =  CostingSigmaDslBuilder.Colls.fromArray(ccc)
      println("after aa")
    }
    
    val ddd = CostingSigmaDslBuilder.Colls.fromArray(z)// CostingSigmaDslBuilder.Colls.fromArray(z, innerErgoType.getRType())
    println("after ddd")  
    import special.sigma._;
    //import JavaHelpers._
    //JavaHelpers.SigmaDsl().Colls().fromArray(z, t)

    //Iso.isoTokensListToPairsColl().from()

    val b = new ErgoBox(
      index = nextBoxId,
      value = toSpend,
      ergoTree = address.getErgoAddress.script,
      creationHeight = chainHeight,
      transactionId = ErgoBox.allZerosModifierId,
      additionalTokens =ddd
         
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
    print("new ID:::")
    val tokenId =   Digest32 @@ boxIdGen.sample.get
    print("new ID: "+ tokenId)
   
      tokenNames += (new ErgoId(tokenId) -> name)
      return TokenInfo(new ErgoId(tokenId), name)
  
    
   // val tokenId = boxIdGen //ObjectGenerators.newErgoId
    
 //   TokenInfo(tokenId, name)
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

//object ObjectGenerators {

//  def newErgoId: Coll[Byte] =
  //  Array.fill(TokenId.size)((scala.util.Random.nextInt(256) - 128).toByte).toColl

//}
