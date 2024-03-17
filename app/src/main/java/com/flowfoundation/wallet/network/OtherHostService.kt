package com.flowfoundation.wallet.network

import com.flowfoundation.wallet.network.model.InboxResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface OtherHostService {

    @GET("/api/data/domain/{domain}")
    suspend fun queryInbox(@Path("domain") domain: String): InboxResponse
}