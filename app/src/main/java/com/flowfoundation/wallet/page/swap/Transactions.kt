package com.flowfoundation.wallet.page.swap

import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.flowjvm.ufix64Safe
import com.flowfoundation.wallet.network.model.SwapEstimateResponse
import com.flowfoundation.wallet.wallet.toAddress
import java.math.BigDecimal

suspend fun swapSend(data: SwapEstimateResponse.Data): String? {
    val binding = swapPageBinding() ?: return ""
    val viewModel = binding.viewModel()
    val tokenKeyFlatSplitPath = data.routes.mapNotNull { it?.route }.flatten()
    val amountInSplit = data.routes.mapNotNull { it?.routeAmountIn }
    val amountOutSplit = data.routes.mapNotNull { it?.routeAmountOut }

    val deadline = System.currentTimeMillis() / 1000 + 60 * 10

    val slippageRate = 0.1f

    val estimateOut = data.tokenOutAmount
    val amountOutMin = estimateOut * (1.0f - slippageRate).toBigDecimal()
    val storageIn = viewModel.fromCoin()?.flowStoragePath
    val storageOut = viewModel.toCoin()?.flowStoragePath
    val outBalancePath = viewModel.toCoin()?.flowBalancePath
    val outReceiverPath = viewModel.toCoin()?.flowReceiverPath

    val estimateIn = data.tokenInAmount
    val amountInMax = estimateIn / (1.0f - slippageRate).toBigDecimal()

    return swapSendInternal(
        swapPaths = tokenKeyFlatSplitPath,
        tokenInMax = amountInMax,
        tokenOutMin = amountOutMin,
        tokenInVaultPath = storageIn?.split("/")?.last() ?: "",
        tokenOutSplit = amountOutSplit,
        tokenInSplit = amountInSplit,
        tokenOutVaultPath = storageOut?.split("/")?.last() ?: "",
        tokenOutReceiverPath = outReceiverPath?.split("/")?.last() ?: "",
        tokenOutBalancePath = outBalancePath?.split("/")?.last() ?: "",
        deadline = deadline,
    )
}

private suspend fun swapSendInternal(
    swapPaths: List<String>,
    tokenInMax: BigDecimal,
    tokenOutMin: BigDecimal,
    tokenInVaultPath: String,
    tokenOutSplit: List<BigDecimal>,
    tokenInSplit: List<BigDecimal>,
    tokenOutVaultPath: String,
    tokenOutReceiverPath: String,
    tokenOutBalancePath: String,
    deadline: Long,
): String? {
    val binding = swapPageBinding() ?: return ""
    val viewModel = binding.viewModel()

    // want use how many token to swap other token
    val isExactFrom = viewModel.exactToken == ExactToken.FROM

    val cadenceScript = (if (isExactFrom) CadenceScript.CADENCE_SWAP_EXACT_TOKENS_TO_OTHER_TOKENS else CadenceScript.CADENCE_SWAP_TOKENS_FROM_EXACT_TOKENS)

    val tokenName = swapPaths.last().split(".").last()
    val tokenAddress = swapPaths.last().split(".")[1].toAddress()
    return cadenceScript.getScript().replace("Token1Name", tokenName).replace("Token1Addr", tokenAddress)
        .transactionByMainWallet(cadenceScript.scriptId) {
            arg { array { swapPaths.map { string(it) } } }

            if (isExactFrom) {
                arg { array(tokenInSplit.map { ufix64Safe(it) }) }
                arg { ufix64Safe(tokenOutMin) }
            } else {
                arg { array(tokenOutSplit.map { ufix64Safe(it) }) }
                arg { ufix64Safe(tokenInMax) }
            }
            arg { ufix64Safe(deadline) }
            arg { path(domain = "storage", identifier = tokenInVaultPath) }
            arg { path(domain = "storage", identifier = tokenOutVaultPath) }
            arg { path(domain = "public", identifier = tokenOutReceiverPath) }
            arg { path(domain = "public", identifier = tokenOutBalancePath) }
        }
}
