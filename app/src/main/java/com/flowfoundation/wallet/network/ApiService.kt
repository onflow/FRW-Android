package com.flowfoundation.wallet.network

import com.flowfoundation.wallet.manager.account.model.EVMTokenBalanceResponse
import com.flowfoundation.wallet.manager.coin.model.TokenPriceResponse
import com.flowfoundation.wallet.manager.cadence.CadenceScriptResponse
import com.flowfoundation.wallet.manager.flowjvm.transaction.PayerSignable
import com.flowfoundation.wallet.network.model.*
import retrofit2.http.*

interface ApiService {

    @POST("/v3/register")
    suspend fun register(@Body param: RegisterRequest): RegisterResponse

    @POST("/v1/user/address")
    suspend fun createWallet(): CreateWalletResponse

    @GET("/v1/user/check")
    suspend fun checkUsername(@Query("username") username: String): UsernameCheckResponse

    @POST("/retoken")
    suspend fun uploadPushToken(@Body token: Map<String, String>): CommonResponse

    @GET("/v2/user/wallet")
    suspend fun getWalletList(): WalletListResponse

    @GET("/v1/user/manualaddress")
    suspend fun manualAddress(): CommonResponse

    @GET("/v1/user/search")
    suspend fun searchUser(@Query("keyword") keyword: String): SearchUserResponse

    @POST("/v3/login")
    suspend fun login(@Body params: LoginRequest): LoginResponse

    @POST("/v3/import")
    suspend fun import(@Body params: ImportRequest): LoginResponse

    @POST("/v3/sync")
    suspend fun syncAccount(@Body params: AccountSyncRequest): CommonResponse

    @POST("/v3/signed")
    suspend fun signAccount(@Body params: AccountSignRequest): CommonResponse

    @GET("/api/v2/nft/list")
    suspend fun getNFTList(
        @Query("address") address: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 25,
    ): NFTListResponse

    @GET("/api/v2/nft/collectionList")
    suspend fun getNFTListOfCollection(
        @Query("address") address: String,
        @Query("collectionIdentifier") collectionIdentifier: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 25,
    ): NFTListResponse

    @GET("/api/v2/nft/id")
    suspend fun getNFTCollections(
        @Query("address") address: String,
    ): NftCollectionsResponse

    @GET("/api/v3/evm/nft/list")
    suspend fun getEVMNFTList(
        @Query("address") address: String,
        @Query("offset") offset: String = "",
        @Query("limit") limit: Int = 25,
    ): NFTListResponse

    @GET("/api/v3/evm/nft/collectionList")
    suspend fun getEVMNFTListOfCollection(
        @Query("address") address: String,
        @Query("collectionIdentifier") collectionIdentifier: String,
        @Query("offset") offset: String = "",
        @Query("limit") limit: Int = 25,
    ): NFTListResponse

    @GET("/api/v3/evm/nft/id")
    suspend fun getEVMNFTCollections(
        @Query("address") address: String,
    ): NftCollectionsResponse

    @GET("/api/v2/nft/collections")
    suspend fun getNFTCollections(): NftCollectionListResponse

    @GET("/v3/nft/favorite")
    suspend fun getNftFavorite(
        @Query("address") address: String,
    ): NftFavoriteResponse

    @PUT("/v2/nft/favorite")
    suspend fun addNftFavorite(@Body params: AddNftFavoriteRequest): CommonResponse

    @POST("/v2/nft/favorite")
    suspend fun updateFavorite(@Body uniqueIds: UpdateNftFavoriteRequest): CommonResponse

    @GET("/v1/user/info")
    suspend fun userInfo(): UserInfoResponse

    @POST("/v1/profile")
    suspend fun updateProfile(@Body params: Map<String, String>): CommonResponse

    @POST("/v1/profile/preference")
    suspend fun updateProfilePreference(@Body params: UpdateProfilePreferenceRequest): CommonResponse

    @GET("/v1/addressbook/contact")
    suspend fun getAddressBook(): AddressBookResponse

    @PUT("/v1/addressbook/external")
    @JvmSuppressWildcards
    suspend fun addAddressBookExternal(@Body params: Map<String, Any?>): CommonResponse

    @PUT("/v1/addressbook/contact")
    @JvmSuppressWildcards
    suspend fun addAddressBook(@Body params: Map<String, Any?>): CommonResponse

    @DELETE("/v1/addressbook/contact")
    suspend fun deleteAddressBook(@Query("id") contactId: String): CommonResponse

    @GET("/v1/coin/rate")
    suspend fun coinRate(@Query("coinId") coinId: Int): CoinRateResponse

    // @doc https://docs.cryptowat.ch/rest-api/
    // @example https://api.cryptowat.ch/markets/binance/btcusdt/price
    @GET("/v1/crypto/map")
    suspend fun price(
        @Query("provider") market: String,
        @Query("pair") coinPair: String
    ): CryptowatchPriceResponse

    // @doc https://docs.cryptowat.ch/rest-api/markets/ohlc
    // @example https://api.cryptowat.ch/markets/binance/btcusdt/ohlc
    // @before @after Unix timestamp
    // @periods Comma separated integers. Only return these time periods. Example: 60,180,108000
    @GET("/v1/crypto/history")
    suspend fun ohlc(
        @Query("provider") market: String,
        @Query("pair") coinPair: String,
        @Query("after") after: Long? = null,
        @Query("period") periods: String? = null,
    ): Map<String, Any>

    // @example https://api.cryptowat.ch/markets/binance/flowusdt/summary
    @GET("/v1/crypto/summary")
    suspend fun summary(
        @Query("provider") market: String,
        @Query("pair") coinPair: String
    ): CryptowatchSummaryResponse

    @GET("/v1/flowns/prepare")
    suspend fun claimDomainPrepare(): ClaimDomainPrepareResponse

    @POST("/v1/flowns/signature")
    suspend fun claimDomainSignature(@Body params: PayerSignable): ClaimDomainSignatureResponse

    @GET("/v1/account/tokentransfers")
    suspend fun getTransferRecordByToken(
        @Query("address") walletAddress: String,
        @Query("token") tokenId: String,
        @Query("limit") limit: Int = 25,
        @Query("after") after: String = "",
    ): TransferRecordResponse

    @GET("/v1/account/transfers")
    suspend fun getTransferRecord(
        @Query("address") walletAddress: String,
        @Query("limit") limit: Int = 25,
        @Query("after") after: String = "",
    ): TransferRecordResponse

    @GET("/api/evm/{evmAddress}/transactions")
    suspend fun getEVMTransferRecord(
        @Path("evmAddress") address: String
    ): EVMTransferRecordResponse

    @POST("/api/template")
    suspend fun securityCadenceCheck(@Body params: CadenceSecurityCheck): CadenceSecurityCheckResponse


    @GET("/api/swap/v1/{network}/estimate")
    suspend fun getSwapEstimate(
        @Path("network") network: String,
        @Query("inToken") inToken: String,
        @Query("outToken") outToken: String,
        @Query("inAmount") inAmount: Float? = null,
        @Query("outAmount") outAmount: Float? = null,
    ): SwapEstimateResponse


    @GET("/v1/crypto/exchange?from=USD")
    suspend fun currency(
        @Query("to") to: String,
    ): CurrencyResponse

    @GET("/v1/user/location")
    suspend fun getDeviceLocation(): LocationInfoResponse

    @GET("/v1/user/device")
    suspend fun getDeviceList(): DeviceListResponse

    @GET("/v1/user/keys")
    suspend fun getKeyDeviceInfo(): KeyDeviceInfoResponse

    @POST("/v3/user/device")
    suspend fun updateDeviceInfo(@Body params: UpdateDeviceParams): CommonResponse

    @GET("/api/prices")
    suspend fun getTokenPrices(): TokenPriceResponse

    @GET("/api/v2/scripts")
    suspend fun getCadenceScript(): CadenceScriptResponse

    @GET("/api/v3/evm/{evmAddress}/fts")
    suspend fun getEVMTokenBalance(
        @Path("evmAddress") address: String,
        @Query("network") network: String
    ): EVMTokenBalanceResponse

    @GET("/v3/checkimport")
    suspend fun checkKeystorePublicKeyImport(@Query("key") publicKey: String): CommonResponse
}