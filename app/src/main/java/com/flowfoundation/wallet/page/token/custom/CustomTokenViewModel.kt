package com.flowfoundation.wallet.page.token.custom

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.app.networkChainId
import com.flowfoundation.wallet.manager.app.networkRPCUrl
import com.flowfoundation.wallet.manager.coin.CustomTokenManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAssociatedFlowIdentifier
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenItem
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenOption
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
    private var isLoading = false

    fun changeOption(option: CustomTokenOption) {
        optionChangeLiveData.postValue(option)
    }

    fun fetchTokenInfoWithAddress(address: String) {
        if (isLoading) {
            return
        }
        isLoading = true
        loadingLiveData.postValue(true)
        if (evmAddressPattern.matches(address)) {
            ioScope {
                fetchEVMTokenInfo(address.lowercase())
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
                importSuccessLiveData.postValue(true)
            }
        }
    }

    private suspend fun fetchEVMTokenInfo(contractAddress: String) = coroutineScope {
        val web3 = Web3j.build(HttpService(networkRPCUrl()))

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

        if (decimalsValue == null) {
            web3.shutdown()
            changeOption(CustomTokenOption.ADDRESS_INPUT)
            loadingLiveData.postValue(false)
            importSuccessLiveData.postValue(false)
            isLoading = false
            return@coroutineScope
        }

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
            contractAddress = contractAddress.lowercase(),
            symbol = symbolValue.orEmpty(),
            decimal = decimalsValue.toInt(),
            name = nameValue.orEmpty(),
            icon = "https://lilico.app/placeholder-2.0.png",
            contractName = null,
            flowIdentifier = flowIdentifier,
            evmAddress = null,
            userId = firebaseUid(),
            userAddress = WalletManager.selectedWalletAddress(),
            chainId = networkChainId(),
            tokenType = TokenType.EVM
        )
        web3.shutdown()
        isLoading = false
        loadingLiveData.postValue(false)
        changeOption(CustomTokenOption.INFO_IMPORT)
    }

    override fun onCleared() {
        isLoading = false
        super.onCleared()
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