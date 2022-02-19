package io.github.dav009.ergopuppet.simulation
import io.github.dav009.ergopuppet.model.BlockchainSimulation
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


class SimulatedBlockchainContext(
    client: ApiClient,
    retrofit: Retrofit,
    explorer: ExplorerApiClient,
    retrofitExplorer: Retrofit,
    nodeInfo: NodeInfo,
    headers: JavaList[BlockHeader],
    blockchain: BlockchainSimulation
) extends BlockchainContextImpl(
      client,
      retrofit,
      explorer,
      retrofitExplorer,
      NetworkType.MAINNET,
      nodeInfo,
      headers
    ) {

  override def signedTxFromJson(json: String): SignedTransaction = ???

  override def getBoxesById(boxIds: String*): Array[InputBox] = ???

  override def getHeight: Int = {
    blockchain.getHeight
  }

  override def getWallet: ErgoWallet = ???

  override def sendTransaction(tx: SignedTransaction): String = {
    val ergoTx: ErgoLikeTransaction = tx.asInstanceOf[SignedTransactionImpl].getTx();
    blockchain.send(ergoTx)
    "something"
  }

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
