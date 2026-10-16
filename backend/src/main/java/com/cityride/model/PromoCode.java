package com.cityride.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "promo_codes")
public class PromoCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    private Instant expiresAt;

    private Integer maxRedemptions;

    @Column(nullable = false)
    private int redemptionCount;

    @Column(nullable = false)
    private boolean enabled = true;

    @Version
    private Long version;

    protected PromoCode() {}

    public PromoCode(String code, BigDecimal discountPercent, Instant expiresAt, Integer maxRedemptions) {
        this.code = code.trim().toUpperCase(java.util.Locale.ROOT);
        this.discountPercent = discountPercent;
        this.expiresAt = expiresAt;
        this.maxRedemptions = maxRedemptions;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public Instant getExpiresAt() { return expiresAt; }
    public Integer getMaxRedemptions() { return maxRedemptions; }
    public int getRedemptionCount() { return redemptionCount; }
    public boolean isEnabled() { return enabled; }
    public boolean isValidAt(Instant now) {
        return enabled && (expiresAt == null || expiresAt.isAfter(now))
                && (maxRedemptions == null || redemptionCount < maxRedemptions);
    }
    public void redeem() {
        if (!isValidAt(Instant.now())) {
            throw new IllegalStateException("Promo code is expired, disabled, or fully redeemed.");
        }
        redemptionCount++;
    }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
