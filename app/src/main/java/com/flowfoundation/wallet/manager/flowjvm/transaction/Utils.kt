package com.flowfoundation.wallet.manager.flowjvm.transaction

import org.onflow.flow.infrastructure.Cadence

class TransactionBuilder {

    internal var scriptId: String? = null

    internal var script: String? = null

    internal var walletAddress: String? = null

    internal var payer: String? = null

    internal var arguments: MutableList<Cadence.Value> = mutableListOf()

    internal var limit: Int? = 9999

    internal var isBridgePayer: Boolean = false

    internal var authorizers: List<String>? = null

    fun scriptId(scriptId: String) {
        this.scriptId = scriptId
    }

    fun script(script: String) {
        this.script = script
    }

    fun arguments(arguments: List<Cadence.Value>) {
        this.arguments = arguments.toMutableList()
    }

    fun arg(argument: Cadence.Value) = arguments.add(argument)

    fun walletAddress(address: String) {
        this.walletAddress = address
    }

    fun payer(payerAddress: String) {
        this.payer = payerAddress
    }

    fun isBridgePayer(isBridgePayer: Boolean) {
        this.isBridgePayer = isBridgePayer
    }

    fun authorizers(authorizers: List<String>) {
        this.authorizers = authorizers
    }

    override fun toString(): String {
        return "TransactionBuilder(scriptId=$scriptId, script=$script, " +
                "walletAddress=$walletAddress, payer=$payer, arguments=${arguments.size} args, limit=$limit)"
    }
}