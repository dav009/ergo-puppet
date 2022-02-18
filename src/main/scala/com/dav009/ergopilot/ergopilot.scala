package com.dav009.ergopilot

import com.dav009.ergopilot.simulation.{DummyBlockchainSimulationImpl, SimulatedErgoClient}
import special.collection.Coll
import scorex.util._

import org.ergoplatform.ErgoAddressEncoder

object Simulator {

  val MinTxFee: Long = 1000 * 1000
  val MinErg: Long   = 1000 * 1000

  implicit val addressEncoder = ErgoAddressEncoder(
    ErgoAddressEncoder.TestnetNetworkPrefix
  )

  def newBlockChainSimulationScenario(scenarioName: String) = {
    val chain      = DummyBlockchainSimulationImpl(scenarioName)
    val ergoClient = SimulatedErgoClient.create(chain)
    (chain, ergoClient)
  }

}
