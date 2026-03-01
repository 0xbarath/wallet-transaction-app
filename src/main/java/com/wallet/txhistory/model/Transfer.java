package com.wallet.txhistory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(nullable = false, length = 32)
    private String network;

    @Column(name = "unique_id", nullable = false, length = 256)
    private String uniqueId;

    @Column(nullable = false, length = 66)
    private String hash;

    @Column(name = "block_num", nullable = false)
    private Long blockNum;

    @Column(name = "block_ts")
    private OffsetDateTime blockTs;

    @Column(name = "from_addr", nullable = false, length = 42)
    private String fromAddr;

    @Column(name = "to_addr", length = 42)
    private String toAddr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Direction direction;

    @Column(length = 64)
    private String asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferCategory category;

    @Column(name = "value_decimal", precision = 38, scale = 18)
    private BigDecimal valueDecimal;

    @Column(name = "raw_value", length = 256)
    private String rawValue;

    @Column(name = "raw_contract_addr", length = 42)
    private String rawContractAddr;

    @Column(name = "raw_contract_decimals")
    private Integer rawContractDecimals;

    @Column(name = "token_id", length = 256)
    private String tokenId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getWalletId() { return walletId; }
    public void setWalletId(UUID walletId) { this.walletId = walletId; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    public String getUniqueId() { return uniqueId; }
    public void setUniqueId(String uniqueId) { this.uniqueId = uniqueId; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public Long getBlockNum() { return blockNum; }
    public void setBlockNum(Long blockNum) { this.blockNum = blockNum; }

    public OffsetDateTime getBlockTs() { return blockTs; }
    public void setBlockTs(OffsetDateTime blockTs) { this.blockTs = blockTs; }

    public String getFromAddr() { return fromAddr; }
    public void setFromAddr(String fromAddr) { this.fromAddr = fromAddr; }

    public String getToAddr() { return toAddr; }
    public void setToAddr(String toAddr) { this.toAddr = toAddr; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public String getAsset() { return asset; }
    public void setAsset(String asset) { this.asset = asset; }

    public TransferCategory getCategory() { return category; }
    public void setCategory(TransferCategory category) { this.category = category; }

    public BigDecimal getValueDecimal() { return valueDecimal; }
    public void setValueDecimal(BigDecimal valueDecimal) { this.valueDecimal = valueDecimal; }

    public String getRawValue() { return rawValue; }
    public void setRawValue(String rawValue) { this.rawValue = rawValue; }

    public String getRawContractAddr() { return rawContractAddr; }
    public void setRawContractAddr(String rawContractAddr) { this.rawContractAddr = rawContractAddr; }

    public Integer getRawContractDecimals() { return rawContractDecimals; }
    public void setRawContractDecimals(Integer rawContractDecimals) { this.rawContractDecimals = rawContractDecimals; }

    public String getTokenId() { return tokenId; }
    public void setTokenId(String tokenId) { this.tokenId = tokenId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public Long getVersion() { return version; }
}
