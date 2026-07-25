# HILO Rewards System - Security

## Secret Management

### Never Commit
- Private keys (`BLOCKCHAIN_SIGNER_PRIVATE_KEY`)
- Database credentials (`SUPABASE_SERVICE_ROLE_KEY`)
- JWT signing secrets
- `.env` files (use `.env.example` as template)

### Wallet Isolation
| Wallet | Role | Purpose |
|--------|------|---------|
| Admin Wallet (deployer) | `ADMIN_ROLE` | Contract administration only |
| Backend Signer Wallet | `EXECUTOR_ROLE` | Sends reward transactions only |
| Multisig (future) | `ADMIN_ROLE` | Contract ownership migration |

**Rules:**
- Admin wallet never sends transactions directly
- Backend signer cannot modify contract roles or pause
- Private keys stored only in server environment variables
- Never log private keys, wallet addresses, or JWTs

## Authentication

### JWT Validation
- Validated via Spring Security OAuth2 Resource Server + NimbusJwtDecoder
- JWKS fetched from `${SUPABASE_URL}/auth/v1/.well-known/jwks.json`
- Only asymmetric algorithms allowed (RSA, EC) — no HMAC/HS256 fallback
- Issuer must match exactly: `${SUPABASE_JWT_ISSUER}`
- Token must not be expired
- Token must not be before `nbf` claim
- Subject (`sub`) must be non-blank
- Key ID (`kid`) must be present in header
- User must exist in `profiles` table with `role = "admin"`

### Authorization
- Only `admin` role users can access `POST /api/v1/rewards/process`
- Non-admin users receive `403 Forbidden`

## Rate Limiting

### Configuration
- **Per-admin**: 5 requests per 15 minutes (configurable via `RATE_LIMIT_MAX_REQUESTS`)
- **Per-group**: 10 requests per hour (configurable via `RATE_LIMIT_MAX_GROUP_REQUESTS`)

### Implementation
- Atomic database upserts prevent race conditions
- PostgreSQL function `atomic_check_rate_limit()` ensures consistency
- **Fail-open**: If database is down, requests proceed (availability over strictness)

## Input Validation

### RewardRequest
- `groupId`: Must be positive Long
- `courseId`: Must be positive Long
- `idempotencyKey`: Must be non-blank string

### Wallet Addresses
- Must start with `0x`
- Must be exactly 42 characters
- Hex character validation (0-9, a-f, A-F)
- Zero address (`0x000...000`) rejected
- Case-insensitive duplicate detection

### Amount Safety
- Amount per recipient is fixed server-side (`REWARDS_AMOUNT_PER_RECIPIENT=10`)
- Frontend cannot influence token amounts
- Decimal precision: 18 (standard ERC-20)

## Idempotency

### Reward ID Generation
```
rewardId = SHA-256(courseId:groupId:idempotencyKey)
```
- Deterministic: same inputs always produce same rewardId
- Format: `0x` prefix + 64 hex characters

### Duplicate Prevention
- **On-chain**: `processedRewards[rewardId]` mapping in RewardWrapper
- **Off-chain**: `UNIQUE(idempotency_key)` and `UNIQUE(transaction_hash)` constraints
- Both layers must agree before processing

## Audit Trail

### What's Logged
- All reward processing attempts (success and failure)
- Rate limiting events
- Authentication failures
- Blockchain transaction hashes
- IP addresses (SHA-256 hashed, never plaintext)

### Storage
- Table: `security_audit_events`
- Fields: user_id, user_role, action, group_id, details, ip_hash, created_at

## Smart Contract Security

### Access Control
```
ADMIN_ROLE (admin wallet)
├── grantRole / revokeRole
├── pause / unpause
├── setMaxRecipientsPerReward
├── renounceRole
└── processReward ← NOT called by admin

EXECUTOR_ROLE (backend signer)
└── processReward ← ONLY this function

PAUSER_ROLE (admin wallet)
└── pause / unpause
```

### Replay Protection
- `processedRewards[rewardId]` prevents double execution
- One reward per transaction (no batching)
- `maxRecipientsPerReward` limits gas usage

### Pausability
- Contract can be paused by PAUSER_ROLE
- Paused contract rejects all processReward calls
- Emergency stop mechanism for detected exploits

## Known Limitations

1. **Fail-Open Rate Limiting**: If database is unavailable, rate limiting is bypassed. Consider circuit breaker pattern for production.

3. **No Multi-Sig**: Admin wallet is single-sig. Migrate to Gnosis Safe for production.

4. **No Transaction Monitoring**: Backend does not poll for transaction confirmation. Implement webhook or polling service.

5. **No Gas Price Oracle**: Gas price estimation is basic. Consider Flashbots or similar for MEV protection.
