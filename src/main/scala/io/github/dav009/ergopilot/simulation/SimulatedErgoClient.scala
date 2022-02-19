package io.github.dav009.ergopilot.simulation

import io.github.dav009.ergopilot.model.BlockchainSimulation
import java.util.function

import org.ergoplatform.appkit.{ErgoClient, BlockchainContext}
import org.ergoplatform.restapi.client.{Parameters, NodeInfo, ApiClient, BlockHeader}
import java.util.ArrayList
import org.ergoplatform.restapi.client.PowSolutions
import java.math.BigInteger

class SimulatedErgoClient(params: Parameters, blockchain: BlockchainSimulation) extends ErgoClient {
  override def execute[T](action: function.Function[BlockchainContext, T]): T = {
    val nodeInfo         = new NodeInfo().parameters(params)
    val client           = new ApiClient("https://somedummy.com")
    val retrofit         = null
    val explorer         = null
    val retrofitExplorer = null
    val headers          = new ArrayList[BlockHeader]()
    val powSolutions     = new PowSolutions();
    powSolutions.setPk("03224c2f2388ae0741be2c50727caa49bd62654dc1f36ee72392b187b78da2c717");
    powSolutions.w("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
    powSolutions.n("20d68047ea27a031");
    powSolutions.d(BigInteger.ZERO);
    val blockId = "78a76fb6c8ac11e7e9da01f2c916b82dd1220a370d7fcfe2df94caa675c21926"
    val firstHeader = new BlockHeader()
      .height(667)
      .nBits(19857408L)
      .difficulty(BigInteger.TEN)
      .id(blockId)
      .parentId(blockId)
      .adProofsRoot(blockId)
      .stateRoot(blockId)
      .transactionsRoot(blockId)
      .version(2)
      .extensionHash(blockId)
      .powSolutions(powSolutions)
      .timestamp(0L)
      .votes("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
    headers.add(firstHeader)

    val ctx = new SimulatedBlockchainContext(
      client,
      retrofit,
      explorer,
      retrofitExplorer,
      nodeInfo,
      headers,
      blockchain
    )
    val res = action.apply(ctx)
    res
  }
}

object SimulatedErgoClient {
  def create(blockchainSim: BlockchainSimulation) = {
    new SimulatedErgoClient(new Parameters(), blockchainSim)
  }
}
