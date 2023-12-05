package io.outblock.lilico.manager.walletconnect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityDappTestBinding
import io.outblock.lilico.utils.isNightMode


class WalletConnectTestActivity: BaseActivity() {
    private lateinit var binding: ActivityDappTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDappTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        setupToolbar()
        binding.tvConnect.setOnClickListener {
            createConnect()
        }
    }

    private fun createConnectPairing() {
        val pairing = CoreClient.Pairing.create()
//        pairing.uri
    }

    private fun createConnect() {
        val pairing: Core.Model.Pairing = CoreClient.Pairing.create { error ->
            throw IllegalStateException("Creating Pairing failed: ${error.throwable.stackTraceToString()}")
        }!!

//        val connectParams =
//            Sign.Params.Connect(
//                namespaces = getNamespaces(),
//                optionalNamespaces = getOptionalNamespaces(),
//                properties = getProperties(),
//                pairing = pairing
//            )
//        val namespaces = requiredNamespaces.map { item ->
//            val caip2Namespace = item.key
//            val proposalNamespace = item.value
//            val accounts = proposalNamespace.chains?.map { "$it:$walletAddress" }.orEmpty()
////        val methods = proposalNamespace.methods.toMutableSet().apply { add("flow_pre_authz") }
//            caip2Namespace to Sign.Model.Namespace.Session(
//                chains = proposalNamespace.chains,
//                accounts = accounts,
//                methods = proposalNamespace.methods,
//                events = proposalNamespace.events
//            )
//        }.toMap()
    }

//    private fun getNamespaces(): Map<String, Sign.Model.Namespace.Proposal> {
//        val namespaces: Map<String, Sign.Model.Namespace.Proposal> =
//            uiState.value
//                .filter { it.isSelected && it.chainId != Chains.POLYGON_MATIC.chainId && it.chainId != Chains.ETHEREUM_KOVAN.chainId }
//                .groupBy { it.chainNamespace }
//                .map { (key: String, selectedChains: List<ChainSelectionUi>) ->
//                    key to Sign.Model.Namespace.Proposal(
//                        chains = selectedChains.map { it.chainId }, //OR uncomment if chainId is an index
//                        methods = selectedChains.flatMap { it.methods }.distinct(),
//                        events = selectedChains.flatMap { it.events }.distinct()
//                    )
//                }.toMap()
//
//        val tmp = uiState.value
//            .filter { it.isSelected && it.chainId == Chains.ETHEREUM_KOVAN.chainId }
//            .groupBy { it.chainId }
//            .map { (key: String, selectedChains: List<ChainSelectionUi>) ->
//                key to Sign.Model.Namespace.Proposal(
//                    methods = selectedChains.flatMap { it.methods }.distinct(),
//                    events = selectedChains.flatMap { it.events }.distinct()
//                )
//            }.toMap()
//
//        return namespaces.toMutableMap().plus(tmp)
//    }
//
//    private fun getOptionalNamespaces() = uiState.value
//        .filter { it.isSelected && it.chainId == Chains.POLYGON_MATIC.chainId }
//        .groupBy { it.chainId }
//        .map { (key: String, selectedChains: List<ChainSelectionUi>) ->
//            key to Sign.Model.Namespace.Proposal(
//                methods = selectedChains.flatMap { it.methods }.distinct(),
//                events = selectedChains.flatMap { it.events }.distinct()
//            )
//        }.toMap()
//
//    private fun getProperties(): Map<String, String> {
//        //note: this property is not used in the SDK, only for demonstration purposes
//        val expiry = (System.currentTimeMillis() / 1000) + TimeUnit.SECONDS.convert(7, TimeUnit.DAYS)
//        return mapOf("sessionExpiry" to "$expiry")
//    }
//
//    fun requestMethod(method: String, sendSessionRequestDeepLink: (Uri) -> Unit) {
//        val (parentChain, chainId, account) = currentState.selectedAccount.split(":")
//        val params: String = when {
//            method.equals("personal_sign", true) -> getPersonalSignBody(account)
//            method.equals("eth_sign", true) -> getEthSignBody(account)
//            method.equals("eth_sendTransaction", true) -> getEthSendTransaction(account)
//            method.equals("eth_signTypedData", true) -> getEthSignTypedData(account)
//            else -> "[]"
//        }
//        val requestParams = Sign.Params.Request(
//            sessionTopic = requireNotNull(WalletDappDelegate.selectedSessionTopic),
//            method = method,
//            params = params, // stringified JSON
//            chainId = "$parentChain:$chainId"
//        )
//
//        WalletConnectSign.request(requestParams,
//            onSuccess = {
//                WalletConnectModal.getActiveSessionByTopic(requestParams.sessionTopic)?.redirect?.toUri()
//                    ?.let { deepLinkUri -> sendSessionRequestDeepLink(deepLinkUri) }
//            },
//            onError = {
//
//            })
//    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, WalletConnectTestActivity::class.java))
        }
    }
}