package com.flowfoundation.wallet.page.token.detail.model

import com.flowfoundation.wallet.network.model.CryptowatchSummaryData
import com.flowfoundation.wallet.page.token.detail.Quote

class TokenDetailChartModel(
    val chartData: List<Quote>? = null,
    val summary: CryptowatchSummaryData.Result? = null,
)