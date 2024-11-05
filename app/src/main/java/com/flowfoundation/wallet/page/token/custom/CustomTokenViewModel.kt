package com.flowfoundation.wallet.page.token.custom

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.coin.CustomTokenManager
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAssociatedFlowIdentifier
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenItem
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenOption
import com.flowfoundation.wallet.utils.evmAddressPattern
import com.flowfoundation.wallet.utils.ioScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import java.math.BigInteger

class CustomTokenViewModel : ViewModel() {

    val optionChangeLiveData = MutableLiveData<CustomTokenOption>()
    val loadingLiveData = MutableLiveData<Boolean>()
    val importSuccessLiveData = MutableLiveData<Boolean>()
    private var currentToken: CustomTokenItem? = null
    val customTokenListLiveData = MutableLiveData<List<CustomTokenItem>>()

    fun changeOption(option: CustomTokenOption) {
        optionChangeLiveData.postValue(option)
    }

    fun fetchTokenInfoWithAddress(address: String) {
        loadingLiveData.postValue(true)
        if (evmAddressPattern.matches(address)) {
            ioScope {
                fetchEVMTokenInfo(address)
            }
        } else {
            // todo add flow custom token
        }
    }

    fun getCurrentToken(): CustomTokenItem? {
        return currentToken
    }

    fun importToken() {
        currentToken?.let {
            if (it.isEnable()) {
                CustomTokenManager.addEVMCustomToken(it)
                FlowCoinListManager.reload()
                importSuccessLiveData.postValue(true)
            }
        }
    }

    private suspend fun fetchEVMTokenInfo(contractAddress: String) = coroutineScope {
        val web3 = Web3j.build(HttpService("https://mainnet.evm.nodes.onflow.org"))

        val decimalsFunction =
            Function("decimals", listOf(), listOf(object : TypeReference<Uint8>() {}))
        val symbolFunction =
            Function("symbol", listOf(), listOf(object : TypeReference<Utf8String>() {}))
        val nameFunction =
            Function("name", listOf(), listOf(object : TypeReference<Utf8String>() {}))

        val decimalsValue = async {
            callSmartContractFunction(
                web3, decimalsFunction,
                contractAddress
            )
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

        currentToken = CustomTokenItem(
            contractAddress = contractAddress,
            symbol = symbolValue.orEmpty(),
            decimal = decimalsValue?.toInt() ?: 0,
            name = nameValue.orEmpty(),
            icon = null,
            contractName = null,
            flowIdentifier = flowIdentifier,
            evmAddress = null
        )
        web3.shutdown()
        loadingLiveData.postValue(false)
        changeOption(CustomTokenOption.INFO_IMPORT)
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