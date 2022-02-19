# Ergo Puppet

![](https://raw.githubusercontent.com/dav009/ergo-puppet/master/puppet.png)

Puppet is an upgrade on the [ergo playground](https://github.com/ergoplatform/ergo-playgrounds). It lets you play and unit test Ergo contracts offchain. Puppet uses as much of AppKit as possible, meaning you should be able to test your code with Puppet with no changes. 

Puppet provides you with a dummy `ErgoClient` and a dummy `BlockchainContext`.


## Usage

Be aware that this project is WIP.

add the following dependencies to your `build.sbt`:

```scala
libraryDependencies += "io.github.dav009" %% "ergopuppet" % "0.0.0+28-8ee0ca24+20220219-2144"  % Test
libraryDependencies += ("org.scorexfoundation" %% "sigma-state" % "4.0.5" ).classifier("tests")
```

## Simple TX Example

Testing a simple transaction.
```scala
import io.github.dav009.ergopuppet.Simulator._

....

val (blockchainSim, ergoClient) = newBlockChainSimulationScenario("Simple Tx")
ergoClient.execute(
    (ctx: BlockchainContext) => { 
    
     // Mocking blockchain state
      val receiverParty = blockchainSim.newParty("receiver", ctx)
      val senderParty = blockchainSim.newParty("sender", ctx)
      senderParty.generateUnspentBoxes(toSpend = 10000000000L)
      
      // making a transaction
      val txBuilder = ctx.newTxBuilder()
      val amountToSpend: Long = Parameters.OneErg
      val newBox = txBuilder
        .outBoxBuilder()
        .value(amountToSpend)
        .contract(
          ctx.compileContract(
            ConstantsBuilder
              .create()
              .item("recPk", receiverParty.wallet.getAddress.asP2PK().pubkey)
              .build(),
            "{ recPk }"
          )
        )
        .build()

      val boxes = senderParty.wallet.getUnspentBoxes(amountToSpend + Parameters.MinFee).get

      val tx: UnsignedTransaction = txBuilder
        .boxesToSpend(boxes)
        .outputs(newBox)
        .fee(Parameters.MinFee)
        .sendChangeTo(P2PKAddress(senderParty.wallet.getAddress.getPublicKey()))
        .build()

      val signed = senderParty.wallet.sign(tx)
      var receiverUnspentCoins =
        blockchainSim.getUnspentCoinsFor(receiverParty.wallet.getAddress)
      assert(receiverUnspentCoins == (0))
      val txId: String = ctx.sendTransaction(signed)
      
      // checking the state of the chain
      val senderPartyUnspentCoins =
        blockchainSim.getUnspentCoinsFor(senderParty.wallet.getAddress)
      receiverUnspentCoins =
        blockchainSim.getUnspentCoinsFor(receiverParty.wallet.getAddress)
      assert(
        senderPartyUnspentCoins == (10000000000L - Parameters.MinFee - amountToSpend)
      )
      assert(receiverUnspentCoins == (amountToSpend))

}
....

```

