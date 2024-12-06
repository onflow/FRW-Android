package com.flowfoundation.wallet.page.token.custom

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.app.networkChainId
import com.flowfoundation.wallet.manager.app.networkRPCUrl
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAssociatedFlowIdentifier
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenItem
import com.flowfoundation.wallet.page.token.custom.model.TokenType
import com.flowfoundation.wallet.utils.evmAddressPattern
import com.flowfoundation.wallet.utils.ioScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import java.math.BigInteger

class WatchAssetsViewModel : ViewModel() {

    val tokenInfoLiveData = MutableLiveData<CustomTokenItem>()
    val balanceWithSymbolLiveData = MutableLiveData<String>()

    fun fetchTokenInfoWithAddress(address: String, icon: String? = null) {
        if (evmAddressPattern.matches(address)) {
            ioScope {
                fetchEVMTokenInfo(address.lowercase(), icon)
            }
        } else {
            // todo add flow custom token
        }
    }


    private suspend fun fetchEVMTokenInfo(contractAddress: String, icon: String?) = coroutineScope {
        val web3 = Web3j.build(HttpService(networkRPCUrl()))

        val decimalsFunction =
            Function("decimals", listOf(), listOf(object : TypeReference<Uint8>() {}))
        val symbolFunction =
            Function("symbol", listOf(), listOf(object : TypeReference<Utf8String>() {}))
        val nameFunction =
            Function("name", listOf(), listOf(object : TypeReference<Utf8String>() {}))

        val balanceFunction = Function(
            "balanceOf",
            listOf(Address(EVMWalletManager.getEVMAddress())),
            listOf(object : TypeReference<Uint256>() {})
        )

        val decimalsValue = async {
            callSmartContractFunction(
                web3, decimalsFunction,
                contractAddress
            )
        }.await()?.value as? BigInteger

        val decimal = decimalsValue?.toInt() ?: 0

        val balanceResult = async {
            callSmartContractFunction(web3, balanceFunction, contractAddress)
        }.await()?.value as? BigInteger

        val symbolValue = async {
            callSmartContractFunction(
                web3, symbolFunction,
                contractAddress
            )
        }.await()?.value as? String
        val nameValue = async {
            callSmartContractFunction(
                web3,
                nameFunction,
                contractAddress
            )
        }.await()?.value as? String


        val flowIdentifier = async {
            cadenceGetAssociatedFlowIdentifier(contractAddress)
        }.await()

        web3.shutdown()
        tokenInfoLiveData.postValue(CustomTokenItem(
            contractAddress = contractAddress.lowercase(),
            symbol = symbolValue.orEmpty(),
            decimal = decimal,
            name = nameValue.orEmpty(),
            icon = icon ?: "https://lilico.app/placeholder-2.0.png",
            contractName = null,
            flowIdentifier = flowIdentifier,
            evmAddress = null,
            userId = firebaseUid(),
            userAddress = EVMWalletManager.getEVMAddress(),
            chainId = networkChainId(),
            tokenType = TokenType.EVM
        ))
        val balance = balanceResult?.toBigDecimal()?.movePointLeft(decimal)?.toFloat() ?: 0f
        balanceWithSymbolLiveData.postValue("$balance $symbolValue")
    }


    private suspend fun callSmartContractFunction(
        web3: Web3j,
        function: Function,
        contractAddress: String
    ): Type<*>? {

        val encodedFunction = FunctionEncoder.encode(function)
        val response = withContext(Dispatchers.IO) {
            web3.ethCall(
                Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).sendAsync().get()
        }

        return if (response.result != null) {
            FunctionReturnDecoder.decode(response.result, function.outputParameters).firstOrNull()
        } else null
    }
}