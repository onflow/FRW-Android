# Flow Wallet Android Architecture Diagram and API Documentation

## Architecture Diagram

```
+--------------------------------------------------------------------------------------------------+
|                                     Flow Wallet Android App                                       |
+--------------------------------------------------------------------------------------------------+
|                                                                                                  |
|  +----------------+    +----------------+    +----------------+    +----------------+            |
|  |                |    |                |    |                |    |                |            |
|  |  UI Components |    |    Managers    |    |    Services    |    |    Database    |            |
|  |                |    |                |    |                |    |                |            |
|  +-------+--------+    +-------+--------+    +-------+--------+    +-------+--------+            |
|          |                     |                     |                     |                      |
|          v                     v                     v                     v                      |
|  +----------------+    +----------------+    +----------------+    +----------------+            |
|  |                |    |                |    |                |    |                |            |
|  |   Activities   |    | AccountManager |    |MessagingService|    |  Cache Layer   |            |
|  |   Fragments    |    | WalletManager  |    |                |    |                |            |
|  |   ViewModels   |    | TokenManager   |    |                |    |                |            |
|  |                |    | NFTManager     |    |                |    |                |            |
|  +----------------+    +----------------+    +----------------+    +----------------+            |
|                                |                                                                 |
|                                v                                                                 |
|  +----------------+    +----------------+    +----------------+    +----------------+            |
|  |                |    |                |    |                |    |                |            |
|  |  Network Layer |    |  FlowCadenceApi|    |  WalletConnect |    |  Third-party   |            |
|  |                |    |                |    |                |    |  Integrations  |            |
|  +-------+--------+    +-------+--------+    +-------+--------+    +-------+--------+            |
|          |                     |                     |                     |                      |
+----------|---------------------|---------------------|---------------------|----------------------+
           |                     |                     |                     |
           v                     v                     v                     v
+----------+---------+ +---------+---------+ +---------+---------+ +---------+---------+
|                    | |                   | |                   | |                   |
| Lilico Backend API | |  Flow Blockchain  | | WalletConnect API | |   Firebase        |
| (API_HOST)         | |                   | |                   | |   Mixpanel        |
| (BASE_HOST)        | |                   | |                   | |   Instabug        |
|                    | |                   | |                   | |   Crowdin         |
+--------------------+ +-------------------+ +-------------------+ +-------------------+
           |                     |                     |                     |
           v                     v                     v                     v
+----------+---------+ +---------+---------+ +---------+---------+ +---------+---------+
|                    | |                   | |                   | |                   |
| FlowNS             | | MoonPay           | | Flowscan          | | Firebase Cloud    |
| Key Indexer        | |                   | |                   | | Functions         |
|                    | |                   | |                   | |                   |
+--------------------+ +-------------------+ +-------------------+ +-------------------+
```

## External API Calls

The Flow Wallet Android app interacts with several external services:

### 1. Lilico Backend API

**Base URLs:**
- API_HOST: https://api.lilico.app (Production) / https://dev.lilico.app (Development)
- BASE_HOST: https://web.api.wallet.flow.com (Production) / https://web-dev.api.wallet.flow.com (Development)

**Key Endpoints:**
- Authentication & User Management:
  - `/register` - Register a new user
  - `/login` - User login
  - `/import` - Import wallet
  - `/checkUsername` - Check username availability
  - `/userInfo` - Get user information
  - `/updateProfile` - Update user profile
  - `/getDeviceList` - Get user's devices
  - `/updateDeviceInfo` - Update device information

- Wallet Operations:
  - `/createWallet` - Create a new wallet
  - `/getWalletList` - Get user's wallets
  - `/syncAccount` - Sync account information
  - `/signAccount` - Sign account

- NFT Management:
  - `/getNFTList` - Get NFTs for an address
  - `/getNFTCollections` - Get NFT collections for an address
  - `/getNftFavorite` - Get favorite NFTs
  - `/addNftFavorite` - Add NFT to favorites
  - `/updateFavorite` - Update favorite NFTs
  - `/getEVMNFTList` - Get EVM NFTs for an address
  - `/getEVMNFTCollections` - Get EVM NFT collections

- Token & Transaction Operations:
  - `/getTransferRecord` - Get transfer records
  - `/getTransferRecordByToken` - Get transfer records for a specific token
  - `/getEVMTransferRecord` - Get EVM transfer records
  - `/getTokenPrices` - Get token prices
  - `/getFlowTokenList` - Get Flow tokens for an address
  - `/getEVMTokenList` - Get EVM tokens for an address
  - `/getEVMTokenBalance` - Get EVM token balances

- Market Data:
  - `/coinRate` - Get coin rates
  - `/price` - Get cryptocurrency prices
  - `/ohlc` - Get OHLC (Open, High, Low, Close) data
  - `/summary` - Get market summary
  - `/currency` - Get currency conversion rates

- Security:
  - `/securityCadenceCheck` - Check Cadence script security

- Swap/Exchange:
  - `/getSwapEstimate` - Get swap estimates

### 2. Flow Blockchain API

The app directly interacts with the Flow blockchain using the Flow SDK through the FlowCadenceApi component:

**Key Operations:**
- `executeCadenceScript` - Execute Cadence scripts on the blockchain
- `getAccount` - Get account information
- `getBlockHeader` - Get block header information
- `getTransaction` - Get transaction information
- `sendTransaction` - Submit transactions to the blockchain
- `waitForSeal` - Wait for transaction confirmation

### 3. FlowNS (Flow Name Service)

**Base URL:** https://flowns.io (Production) / https://testnet.flowns.io (Testnet)

**Key Endpoints:**
- `/api/data/domain/{domain}` - Query domain information

### 4. Flow Key Indexer

**Base URL:** https://production.key-indexer.flow.com

**Key Endpoints:**
- `/key/{publicKey}` - Query address information for a public key

### 5. Firebase Cloud Functions

**Base URL:** https://us-central1-lilico-334404.cloudfunctions.net/ (Production) / https://us-central1-lilico-dev.cloudfunctions.net/ (Development)

**Key Functions:**
- `signAsPayer` - Sign transactions as a payer
- `/api/signAsBridgeFeePayer` - Sign bridge transactions as a fee payer
- `moonPaySignature` - Generate signatures for MoonPay integration

### 6. Flowscan

The app doesn't directly call Flowscan's API but opens Flowscan URLs in a browser:

**URLs:**
- https://flowscan.io/tx/{transactionId} (Mainnet)
- https://testnet.flowscan.io/tx/{transactionId} (Testnet)
- https://evm.flowscan.io/tx/{transactionId} (EVM Mainnet)
- https://evm-testnet.flowscan.io/tx/{transactionId} (EVM Testnet)
- https://flowscan.io/account/{address} (Mainnet)
- https://testnet.flowscan.io/account/{address} (Testnet)
- https://evm.flowscan.io/address/{address} (EVM Mainnet)
- https://evm-testnet.flowscan.io/address/{address} (EVM Testnet)
- https://www.flowscan.io/ft/token/{tokenIdentifier} (Fungible Tokens)
- https://evm.flowscan.io/token/{tokenAddress} (EVM Tokens)

### 7. Third-Party Integrations

The app integrates with several third-party services:

- **Firebase** - Analytics, cloud messaging, remote config
- **Mixpanel** - User analytics
- **Instabug** - Crash reporting and feedback
- **Crowdin** - Localization
- **WalletConnect** - For connecting to dApps
- **MoonPay** - For purchasing cryptocurrency with fiat

## Data Flow

1. **User Authentication Flow:**
   - User registers/logs in through the UI
   - App sends credentials to Lilico Backend API
   - API validates and returns authentication tokens
   - App stores tokens for subsequent API calls

2. **Wallet Management Flow:**
   - App creates/imports wallets through Lilico Backend API
   - WalletManager maintains wallet state
   - AccountManager handles account-related operations

3. **Transaction Flow:**
   - User initiates a transaction
   - App builds transaction using FlowCadenceApi
   - Transaction is signed locally
   - Signed transaction is sent to Flow blockchain
   - App monitors transaction status using waitForSeal
   - Transaction result is displayed to user

4. **NFT/Token Management Flow:**
   - App fetches NFT/token data from Lilico Backend API
   - Data is cached locally
   - UI displays NFTs/tokens to user
   - User actions (like favoriting) are sent back to API

5. **External Service Integration Flow:**
   - Firebase provides analytics and messaging
   - Mixpanel tracks user events
   - Instabug handles crash reporting
   - Crowdin manages localization
   - WalletConnect enables dApp connections
   - MoonPay facilitates crypto purchases