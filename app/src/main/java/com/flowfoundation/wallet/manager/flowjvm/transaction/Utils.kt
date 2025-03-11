package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.nftco.flow.sdk.cadence.Field
import com.nftco.flow.sdk.cadence.JsonCadenceBuilder

class TransactionBuilder {

    internal var script: String? = null

    internal var walletAddress: String? = null

    internal var payer: String? = null

    internal var arguments: MutableList<Field<*>> = mutableListOf()

    internal var limit: Int? = 9999

    fun script(script: String) {
        this.script = script
    }

    fun arguments(arguments: MutableList<Field<*>>) {
        this.arguments = arguments
    }

    fun arguments(arguments: JsonCadenceBuilder.() -> Iterable<Field<*>>) {
        val builder = JsonCadenceBuilder()
        this.arguments = arguments(builder).toMutableList()
    }

    fun arg(argument: Field<*>) = arguments.add(argument)

    fun arg(argument: JsonCadenceBuilder.() -> Field<*>) = arg(argument(JsonCadenceBuilder()))

    fun walletAddress(address: String) {
        this.walletAddress = address
    }

    fun payer(payerAddress: String) {
        this.payer = payerAddress
    }

    override fun toString(): String {
        return "TransactionBuilder(script=$script, walletAddress=$walletAddress, payer=$payer, arguments=${arguments.map { "${it.type}:${it.value}" }}, limit=$limit)"
    }
}