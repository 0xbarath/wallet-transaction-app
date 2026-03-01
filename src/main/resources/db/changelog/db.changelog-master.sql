--liquibase formatted sql

--changeset wallet-app:001-create-wallets
CREATE TABLE wallets (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    address VARCHAR(42) NOT NULL,
    network VARCHAR(32) NOT NULL,
    label VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wallets_address_network UNIQUE (address, network)
);

--changeset wallet-app:002-create-transfers
CREATE TABLE transfers (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    wallet_id UUID NOT NULL,
    network VARCHAR(32) NOT NULL,
    unique_id VARCHAR(256),
    hash VARCHAR(66) NOT NULL,
    block_num BIGINT NOT NULL,
    block_ts TIMESTAMP WITH TIME ZONE,
    from_addr VARCHAR(42) NOT NULL,
    to_addr VARCHAR(42),
    direction VARCHAR(3) NOT NULL,
    asset VARCHAR(64),
    category VARCHAR(16) NOT NULL,
    value_decimal NUMERIC(38, 18),
    raw_value VARCHAR(256),
    raw_contract_addr VARCHAR(42),
    raw_contract_decimals INTEGER,
    token_id VARCHAR(256),
    dedupe_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfers_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    CONSTRAINT uq_transfers_network_dedupe UNIQUE (network, dedupe_hash)
);

--changeset wallet-app:003-create-wallet-sync-state
CREATE TABLE wallet_sync_state (
    wallet_id UUID PRIMARY KEY,
    last_synced_block BIGINT,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    sync_in_progress BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sync_state_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

--changeset wallet-app:004-create-indexes
CREATE INDEX idx_transfers_wallet_created ON transfers (wallet_id, created_at DESC);
CREATE INDEX idx_transfers_wallet_asset_created ON transfers (wallet_id, asset, created_at DESC);
CREATE INDEX idx_transfers_wallet_category_created ON transfers (wallet_id, category, created_at DESC);
CREATE INDEX idx_transfers_wallet_block ON transfers (wallet_id, block_num DESC);

--changeset wallet-app:005-add-version-columns
ALTER TABLE wallets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE transfers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

--changeset wallet-app:006-replace-dedupe-with-unique-id
UPDATE transfers SET unique_id = dedupe_hash WHERE unique_id IS NULL;
ALTER TABLE transfers ALTER COLUMN unique_id SET NOT NULL;
ALTER TABLE transfers DROP CONSTRAINT uq_transfers_network_dedupe;
ALTER TABLE transfers DROP COLUMN dedupe_hash;
ALTER TABLE transfers ADD CONSTRAINT uq_transfers_network_unique_id UNIQUE (network, unique_id);

--changeset wallet-app:007-create-address-labels
CREATE TABLE address_labels (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    network VARCHAR(32) NOT NULL,
    address VARCHAR(64) NOT NULL,
    protocol VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    source VARCHAR(64) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_address_labels_network_address UNIQUE (network, address)
);
CREATE INDEX idx_address_labels_network_protocol ON address_labels (network, protocol);

--changeset wallet-app:008-seed-address-labels
INSERT INTO address_labels (id, network, address, protocol, label, category, source, confidence)
VALUES
  (RANDOM_UUID(), 'eth-mainnet', '0x87870bca3f3fd6335c3f4ce8392d69350b4fa4e2', 'aave-v3', 'Aave V3: Pool', 'lending', 'curated', 0.9900),
  (RANDOM_UUID(), 'eth-mainnet', '0x7d2768de32b0b80b7a3454c06bdac94a69ddc7a9', 'aave-v2', 'Aave V2: Lending Pool', 'lending', 'curated', 0.9900),
  (RANDOM_UUID(), 'eth-mainnet', '0x68b3465833fb72a70ecdf485e0e4c7bd8665fc45', 'uniswap-v3', 'Uniswap V3: Router 2', 'dex', 'curated', 0.9900),
  (RANDOM_UUID(), 'eth-mainnet', '0xe592427a0aece92de3edee1f18e0157c05861564', 'uniswap-v3', 'Uniswap V3: Router', 'dex', 'curated', 0.9900),
  (RANDOM_UUID(), 'eth-mainnet', '0x7a250d5630b4cf539739df2c5dacb4c659f2488d', 'uniswap-v2', 'Uniswap V2: Router', 'dex', 'curated', 0.9900),
  (RANDOM_UUID(), 'eth-mainnet', '0xae7ab96520de3a18e5e111b5eaab095312d7fe84', 'lido', 'Lido: stETH', 'staking', 'curated', 0.9900),
  (RANDOM_UUID(), 'eth-mainnet', '0xc3d688b66703497daa19211eedff47f25384cdc3', 'compound-v3', 'Compound V3: cUSDCv3', 'lending', 'curated', 0.9900),
  (RANDOM_UUID(), 'eth-mainnet', '0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2', 'weth', 'WETH', 'token', 'curated', 0.9900);
