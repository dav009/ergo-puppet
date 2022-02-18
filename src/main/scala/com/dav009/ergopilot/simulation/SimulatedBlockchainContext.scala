package com.dav009.ergopilot.simulation
import com.dav009.ergopilot.model.BlockchainSimulation
import org.ergoplatform.restapi.client.{ApiClient, NodeInfo}
import retrofit2.Retrofit
import java.util.{List => JavaList}
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.{
  Address,
  NetworkType,
  UnsignedTransaction,
  InputBox,
  SignedTransaction,
  ErgoWallet
}
import org.ergoplatform.restapi.client.BlockHeader
import org.ergoplatform.explorer.client.ExplorerApiClient;
import org.ergoplatform.{ErgoLikeTransaction}
import org.ergoplatform.appkit.impl.{SignedTransactionImpl, InputBoxImpl}
import java.util;
import scala.collection.JavaConverters._


  //.serialization.generators.ObjectGenerators

class SimulatedBlockchainContext(
    client: ApiClient,
    retrofit: Retrofit,
    explorer: ExplorerApiClient,
    retrofitExplorer: Retrofit,
    nodeInfo: NodeInfo,
    headers: JavaList[BlockHeader],
    blockchain: BlockchainSimulation
) extends BlockchainContextImpl (
      client,
      retrofit,
      explorer,
      retrofitExplorer,
      NetworkType.MAINNET,
      nodeInfo,
      headers
    )  {

  // ok
  // override def getNodeInfo: NodeInfo = {
  //  nodeInfo
  //}

  // ok
  override def signedTxFromJson(json: String): SignedTransaction = ???

  // need to use blockcahin
  override def getBoxesById(boxIds: String*): Array[InputBox] = ???

  // ok
  //override def newProverBuilder(): ErgoProverBuilder = ???

  // need to use blockchain
  override def getHeight: Int = {
    blockchain.getHeight
  }

  // ok
  override def getWallet: ErgoWallet = ???

  // need to use blockchian
  override def sendTransaction(tx: SignedTransaction): String = {
    val ergoTx: ErgoLikeTransaction = tx.asInstanceOf[SignedTransactionImpl].getTx();
    blockchain.send(ergoTx)
    "something"
  }

  // ok
  //override def createPreHeader(): PreHeaderBuilder = ???

  //ok
  //override def getCoveringBoxesFor(address: Address,
  //                                 amountToSpend: Long,
  //                                 tokensToSpend: util.List[ErgoToken]): CoveringBoxes = ???

  // need to use blockchain
  // done
  override def getUnspentBoxesFor(
      address: Address,
      offset: Int,
      limit: Int
  ): util.List[InputBox] = {
    val ergoBoxes = blockchain.selectUnspentBoxesFor(address)
    ergoBoxes.map { box =>
      new InputBoxImpl(this, box)
    }.map(_.asInstanceOf[InputBox]).asJava
  }

}
