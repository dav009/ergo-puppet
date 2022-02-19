package io.github.dav009.ergopilot.model

import org.ergoplatform.appkit.{
  Address,
  UnsignedTransaction,
  SignedTransaction,
  BlockchainContext,
  Mnemonic,
  InputBox,
  ErgoWallet
}

import org.ergoplatform.{ErgoBox, ErgoLikeTransaction}
import scorex.util._
import special.collection.Coll
import scorex.util.encode.Base16
import org.ergoplatform.appkit.ErgoId

case class TokenInfo(tokenId: ErgoId, tokenName: String)

case class TokenAmount(token: TokenInfo, tokenAmount: Long) {
  override def toString: String =
    s"(${Base16.encode(token.tokenId.getBytes())}, $tokenAmount)"
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

trait BlockchainSimulation {

  def newParty(name: String, ctx: BlockchainContext): Party

  def newToken(name: String): TokenInfo

  def send(tx: ErgoLikeTransaction): Unit

  def setHeight(height: Int): Unit

  def getHeight: Int

  def selectUnspentBoxesFor(
      address: Address
  ): List[ErgoBox]
}

trait Party {
  def name: String
  def wallet: Wallet

  def generateUnspentBoxes(
      toSpend: Long,
      tokensToSpend: List[TokenAmount] = List()
  ): Unit

  def selectUnspentBoxes(
      toSpend: Long,
      tokensToSpend: List[TokenAmount] = List()
  ): List[ErgoBox]

  def printUnspentAssets(): Unit
}

trait Wallet extends ErgoWallet {
  def name: String

  def getAddress: Address

  def sign(tx: UnsignedTransaction): SignedTransaction
}
