package com.flowfoundation.wallet.network

import com.flowfoundation.wallet.network.model.InboxResponse
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddressResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface OtherHostService {

    @GET("/api/data/domain/{domain}")
    suspend fun queryInbox(@Path("domain") domain: String): InboxResponse

    @GET("/key/{publicKey}")
    suspend fun queryAddress(@Path("publicKey") publicKey: String): KeystoreAddressResponse
}