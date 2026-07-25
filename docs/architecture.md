# HILO Rewards System - Architecture

## Overview

The HILO Rewards System distributes ERC-20 tokens (HILO) to students when they complete group-based courses. The system combines a Java Spring Boot backend, Supabase PostgreSQL database, and Solidity smart contracts on Arbitrum Sepolia.

```
┌─────────────────┐     ┌─────────────────┐     ┌──────────────────┐
│   Frontend      │────▶│   Backend       │────▶│   Arbitrum       │
│   (React/Next)  │     │   (Spring Boot) │     │   Sepolia RPC    │
└─────────────────┘     └────────┬────────┘     └────────┬─────────┘
                                 │                        │
                                 ▼                        ▼
                        ┌─────────────────┐     ┌──────────────────┐
                        │   Supabase      │     │   RewardWrapper  │
                        │   PostgreSQL    │     │   Contract       │
                        └─────────────────┘     └────────┬─────────┘
                                                        │
                                                        ▼
                                                ┌──────────────────┐
                                                │   HiloToken      │
                                                │   Contract       │
                                                └──────────────────┘
```

## Components

### Smart Contracts (Solidity 0.8.27)

**HiloToken.sol** — ERC-20 token with OpenZeppelin v5.6.0
- `AccessControl` for role management
- `MINTER_ROLE` — assigned to RewardWrapper
- `mint(address to, uint256 amount)` — only callable by MINTER_ROLE holders
- No reward logic embedded; purely a token

**RewardWrapper.sol** — Access control and business logic
- `ADMIN_ROLE` — wallet admin, can grant/revoke roles, pause, update limits
- `EXECUTOR_ROLE` — backend signer wallet, can call processReward
- `PAUSER_ROLE` — can pause/unpause the contract
- `processReward(bytes32 rewardId, address[] recipients, uint256 amountPerRecipient)` — mints tokens to each recipient in a single transaction
- `processedRewards` mapping — prevents double-claiming
- `maxRecipientsPerReward` — gas safety limit, configurable by ADMIN_ROLE

**Roles hierarchy:**
```
Admin Wallet (deployer)
├── ADMIN_ROLE (default)
├── EXECUTOR_ROLE → granted to Backend Signer Wallet
├── PAUSER_ROLE
└── MINTER_ROLE → granted to RewardWrapper, then renounced by admin
```

### Backend (Spring Boot 3.4.0 + Java 21)

**Package structure:**
```
com.hilo.rewards
├── HiloRewardsApplication.java
├── config/
│   ├── CorsConfig.java
│   ├── SecurityConfig.java
│   └── Web3Config.java
├── controller/
│   └── RewardController.java
├── exception/
│   ├── BusinessException.java
│   ├── ErrorCode.java
│   ├── ErrorResponse.java
│   └── GlobalExceptionHandler.java
├── model/
│   ├── Course.java
│   ├── Group.java
│   ├── GroupMember.java
│   ├── Profile.java
│   ├── RewardRequest.java
│   └── RewardResponse.java
├── repository/
│   └── SupabaseRepository.java
├── security/
│   ├── AuthenticationFilter.java
│   ├── JwtValidator.java
│   └── RateLimiter.java
└── service/
    ├── BlockchainService.java
    └── RewardService.java
```

**API endpoint:**
```
POST /api/v1/rewards/process
Headers: Authorization: Bearer <supabase_jwt>
Body: { "groupId": 100, "courseId": 1, "idempotencyKey": "unique-key" }
```

**Dependencies:**
- `web3j 4.12.2` — Ethereum interaction
- `java-jwt 4.4.0` — JWT validation
- `spring-boot-starter-jdbc` — PostgreSQL queries via JdbcTemplate
- `spring-boot-starter-security` — Filter chain

### Database (Supabase PostgreSQL)

**Tables:**
| Table | Purpose |
|-------|---------|
| `profiles` | User profiles with `wallet_address` (unique) and `role` enum |
| `courses` | Course definitions with `is_active` flag |
| `groups` | Groups with `estado` enum (pending/completed/processing/failed) |
| `group_members` | Many-to-many with `UNIQUE(group_id, user_id)` and `approved` flag |
| `rewards` | Reward records with `UNIQUE(idempotency_key)` and `UNIQUE(transaction_hash)` |
| `reward_recipients` | Per-wallet amounts for each reward |
| `reward_attempts` | Blockchain transaction attempt log |
| `security_audit_events` | Audit trail for all operations |
| `api_rate_limits` | Persistent rate limiting counters |

**RLS Policies:** Row-level security enabled on all tables with role-based access.

**Database functions:**
- `atomic_check_rate_limit()` — atomic upsert for rate limiting, SECURITY DEFINER

## Reward Flow

```
1. Frontend sends POST /api/v1/rewards/process
   { groupId, courseId, idempotencyKey }

2. AuthenticationFilter
   ├── Extract JWT from Authorization header
   ├── Validate JWT via JwtValidator
   ├── Fetch user role from profiles table
   └── Reject if role != "admin"

3. RewardService.processReward()
   ├── 1. Rate Limiting
   │   ├── Check per-admin rate limit (5 req/15min)
   │   └── Check per-group rate limit (10 req/1hr)
   ├── 2. Idempotency Check
   │   └── Generate rewardId = SHA-256(courseId:groupId:idempotencyKey)
   │   └── Check if reward already processed on-chain
   ├── 3. Validate Group
   │   └── Must exist with estado="completed"
   ├── 4. Validate Course
   │   └── Must exist and be active
   ├── 5. Get Group Members
   │   └── Must be non-empty, all approved=true
   ├── 6. Validate Wallets
   │   ├── Each member must have valid wallet_address
   │   ├── Checksum validation via web3j
   │   ├── No duplicate wallets (case-insensitive)
   │   └── No zero address
   ├── 7. Calculate Amounts
   │   └── amountPerRecipient = 10 * 10^18 (10 HILO tokens)
   ├── 8. Check ETH Balance
   │   └── Signer must have ≥ 0.0021 ETH for gas
   ├── 9. Save Reward (processing state)
   │   └── Insert into rewards, reward_recipients, update groups
   ├── 10. Send Blockchain Transaction
   │   ├── Encode processReward(rewardId, recipients, amount)
   │   ├── Send via web3j
   │   └── Save txHash to rewards table
   └── 11. Return RewardResponse
       { rewardId, status: "submitted", transactionHash, recipients }
```

## Security Model

### Authentication & Authorization
- **JWT validation**: Supabase JWT validated for issuer and expiration
- **Role check**: Only users with `role = "admin"` in profiles table can access rewards API
- **Backend signer**: Separate wallet from admin; only has EXECUTOR_ROLE

### Wallet Isolation
```
Admin Wallet (deployer)     → ADMIN_ROLE only
Backend Signer Wallet       → EXECUTOR_ROLE only
Multisig (future)           → ADMIN_ROLE (after migration)
```

### Rate Limiting
- **Per-admin**: 5 requests per 15 minutes (configurable)
- **Per-group**: 10 requests per hour (configurable)
- **Implementation**: Atomic database upserts via PostgreSQL function
- **Fail-open**: If rate limit DB is down, requests are allowed

### Idempotency
- **Reward ID**: `SHA-256(courseId:groupId:idempotencyKey)` — deterministic, not random
- **On-chain check**: `processedRewards[rewardId]` mapping in RewardWrapper
- **Off-chain check**: `rewards.transaction_hash IS NOT NULL` in database
- **Prevents**: Duplicate token minting for same logical reward

### Audit Trail
- All operations logged to `security_audit_events` table
- IP addresses stored as SHA-256 hashes (never plaintext)
- Events: rate_limited, idempotent_hit, group_not_found, reward_created, transaction_sent, transaction_failed

### Input Validation
- `groupId`, `courseId`: Must be positive Long
- `idempotencyKey`: Must be non-blank
- Wallet addresses: 0x prefix, 42 chars, checksum validation, no zero address
- Amounts: Fixed at 10 HILO per recipient, never from frontend

## Deployment

### Prerequisites
- Node.js 20+ and npm
- Java 21 and Maven
- Funded admin wallet on Arbitrum Sepolia (0.01 ETH recommended)
- Funded backend signer wallet on Arbitrum Sepolia (0.005 ETH recommended)
- Supabase project with database

### Contract Deployment
```bash
npm install
npx hardhat compile
npx hardhat test
npx hardhat run scripts/deploy.ts --network arbitrumSepolia
```

### Environment Variables
```bash
# Backend only (never frontend)
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key

BLOCKCHAIN_RPC_URL=https://sepolia-rollup.arbitrum.io/rpc
BLOCKCHAIN_CHAIN_ID=421614
BLOCKCHAIN_SIGNER_PRIVATE_KEY=0x...
BLOCKCHAIN_WRAPPER_CONTRACT_ADDRESS=0x...

REWARDS_AMOUNT_PER_RECIPIENT=10
REWARDS_DECIMALS=18

RATE_LIMIT_MAX_REQUESTS=5
RATE_LIMIT_WINDOW_SECONDS=900
RATE_LIMIT_MAX_GROUP_REQUESTS=10
RATE_LIMIT_GROUP_WINDOW_SECONDS=3600

ALLOWED_ORIGINS=http://localhost:3000
```

### Backend Deployment
```bash
cd backend
mvn clean package
java -jar target/hilo-rewards-1.0.0-SNAPSHOT.jar
```

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| UNAUTHORIZED | 401 | Authentication required |
| FORBIDDEN | 403 | Admin role required |
| INVALID_INPUT | 400 | Invalid request data |
| GROUP_NOT_FOUND | 404 | Group does not exist |
| COURSE_INACTIVE | 400 | Course is not active |
| GROUP_EMPTY | 400 | Group has no members |
| GROUP_NOT_COMPLETED | 400 | Not all members approved |
| MEMBER_WITHOUT_WALLET | 400 | Member missing wallet |
| INVALID_WALLET | 400 | Invalid wallet format/checksum |
| DUPLICATE_WALLET | 400 | Same wallet in multiple members |
| REWARD_ALREADY_EXISTS | 409 | Reward already processed |
| RATE_LIMITED | 429 | Too many requests |
| CONTRACT_ERROR | 500 | Blockchain transaction failed |
| INSUFFICIENT_GAS | 500 | Signer has insufficient ETH |
| INTERNAL_ERROR | 500 | Unexpected server error |
