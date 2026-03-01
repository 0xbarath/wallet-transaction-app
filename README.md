# Wallet Transaction History Service

A Spring Boot service that registers EVM wallet addresses, syncs transaction history from Alchemy's Transfers API, and serves transaction history via filtered queries and natural-language prompts with RBAC enforcement.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for Postgres and integration tests)

## Quick Start

### Local (H2 in-memory)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Swagger UI: http://localhost:8080/swagger-ui.html
H2 Console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:walletdb`)

### Docker Compose (Postgres)

```bash
cp .env.example .env
# Edit .env with your Alchemy API key
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## API Usage

All API endpoints require `X-Auth-WalletAccess: allow` header.

### Register a wallet

```bash
curl -X POST localhost:8080/v1/wallets \
  -H "X-Auth-WalletAccess: allow" \
  -H "Content-Type: application/json" \
  -d '{"address":"0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045","network":"eth-mainnet","label":"vitalik"}'
```

### Sync transactions

```bash
curl -X POST localhost:8080/v1/wallets/{walletId}/sync \
  -H "X-Auth-WalletAccess: allow" \
  -H "Content-Type: application/json" \
  -d '{"lookbackDays":30}'
```

### Query transactions (admin)

```bash
curl "localhost:8080/v1/transactions?walletId={id}&limit=10" \
  -H "X-Auth-WalletAccess: allow" \
  -H "X-Role: admin"
```

### Query transactions (user) - addresses masked, no internal transfers

```bash
curl "localhost:8080/v1/transactions?walletId={id}&limit=10" \
  -H "X-Auth-WalletAccess: allow"
```

### Natural language query

```bash
curl -X POST localhost:8080/v1/transactions:query \
  -H "X-Auth-WalletAccess: allow" \
  -H "Content-Type: application/json" \
  -d '{"walletId":"...","prompt":"show outgoing USDC above 100 last 7 days"}'
```

## Architecture

- **API Layer**: WalletController, TransactionController with OpenAPI annotations
- **Security**: RequestIdFilter -> AuthFilter -> RbacFilter chain
- **Application**: WalletService, SyncService, TransactionQueryService, PromptParserService
- **Domain**: JPA entities (Wallet, Transfer, WalletSyncState), QuerySpec, cursor pagination
- **Infrastructure**: AlchemyClient (WebClient JSON-RPC), Liquibase migrations, Micrometer metrics

## RBAC

| Feature | Admin | User |
|---------|-------|------|
| Full addresses | Yes | Masked (0x1234...abcd) |
| Internal transfers | Yes | Hidden |
| Raw contract details | Yes | Hidden |

## Prompt Parser

The deterministic Tier 1 parser supports:
- Direction: incoming/outgoing/sent/received/deposits/withdrawals
- Time: today, yesterday, last N days, last week, last month
- Amount: above/below/greater than/less than + value
- Assets: ETH, USDC, USDT, DAI, WETH, WBTC, etc.
- Categories: erc20, erc721, nft, internal, external
- Counterparty: to/from + 0x address

## Transaction Enrichment (Explain API)

Admin-only endpoint that explains what a transaction does by fetching on-chain evidence and optionally generating an LLM explanation with strict anti-hallucination guardrails.

### Explain a transaction

```bash
curl -X POST localhost:8080/v1/transactions/explain \
  -H "X-Auth-WalletAccess: allow" \
  -H "X-Role: admin" \
  -H "Content-Type: application/json" \
  -d '{"txHash":"0x...","network":"eth-mainnet","explain":true}'
```

### Deterministic-only (no LLM)

```bash
curl -X POST localhost:8080/v1/transactions/explain \
  -H "X-Auth-WalletAccess: allow" \
  -H "X-Role: admin" \
  -H "Content-Type: application/json" \
  -d '{"txHash":"0x...","explain":false}'
```

### Response statuses
- **ENRICHED** — receipt fetched, labels matched, operation classified, LLM explanation validated
- **PARTIAL** — deterministic pipeline succeeded but LLM explanation failed validation or was skipped
- **FAILED** — transaction receipt not found on-chain

### Guardrails
- Evidence-only grounding: LLM receives a pre-built evidence bundle, never accesses the chain
- Mandatory citation: every claim must reference specific evidence IDs
- Post-validation: phantom addresses, non-existent evidence IDs, and invalid JSON are rejected
- Kill switch: set `explain=false` or omit `ANTHROPIC_API_KEY` to disable LLM

### Supported protocol interactions
- **Aave V3**: Supply, Withdraw, Borrow, Repay, Liquidation, FlashLoan
- **Uniswap V2/V3**: Swap
- **Lido**: stETH staking
- **Compound V3**: cUSDCv3 lending
- **WETH**: Wrap/unwrap

## Testing

```bash
# Unit tests
mvn test

# Integration tests (requires Docker)
mvn verify
```
