package io.outblock.lilico.network

import io.outblock.lilico.manager.flowjvm.transaction.PayerSignable
import io.outblock.lilico.network.model.*
import retrofit2.http.*

interface ApiService {

    @POST("/v1/register")
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

    @POST("/v2/login")
    suspend fun login(@Body params: Map<String, String>): LoginResponse

    @GET("/v1/account/info")
    suspend fun getAddressInfo(@Query("address") address: String): AddressInfoResponse

    @GET("/api/nft/list")
    suspend fun nftList(
        @Query("address") address: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 25,
    ): NFTListResponse

    @GET("/api/nft/collectionList")
    suspend fun nftsOfCollection(
        @Query("address") address: String,
        @Query("collectionIdentifier") collectionIdentifier: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 25,
    ): NFTListResponse

    @GET("/api/nft/id")
    suspend fun nftCollectionsOfAccount(
        @Query("address") address: String,
    ): NftCollectionsResponse

    @GET("/v2/nft/detail")
    suspend fun nftMeta(
        @Query("address") address: String,
        @Query("nftCollection") contractName: String,
        @Query("nftID") tokenId: String,
    ): CommonResponse

    @GET("/api/nft/collections")
    suspend fun nftCollections(): NftCollectionListResponse

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

    @POST("/v1/addressbook/contact")
    @JvmSuppressWildcards
    suspend fun editAddressBook(@Body params: Map<String, Any>): CommonResponse

    @GET("/v1/coin/rate")
    suspend fun coinRate(@Query("coinId") coinId: Int): CoinRateResponse

    @GET("/v1/coin/map")
    suspend fun coinMap(): CoinMapResponse

    // @doc https://docs.cryptowat.ch/rest-api/
    // @example https://api.cryptowat.ch/markets/binance/btcusdt/price
    @GET("/v1/crypto/map")
    suspend fun price(@Query("provider") market: String, @Query("pair") coinPair: String): CryptowatchPriceResponse

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
    suspend fun summary(@Query("provider") market: String, @Query("pair") coinPair: String): CryptowatchSummaryResponse

    @POST("/v1/account/query")
    @JvmSuppressWildcards
    suspend fun flowScanQuery(@Body params: String): String

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

    @POST("/v1/user/address/sandboxnet")
    suspend fun enableSandboxNet(
    ): SandboxEnableResponse
}