package com.cityride.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, unique = true)
    private Ride ride;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(unique = true)
    private String stripeCheckoutSessionId;

    private String stripePaymentIntentId;

    @Column(nullable = false, updatable = false)
    private Instant paidAt = Instant.now();

    protected Payment() {}

    public Payment(Ride ride, BigDecimal amount) {
        this.ride = ride;
        this.amount = amount;
    }
    public Payment(Ride ride, BigDecimal amount, String stripeCheckoutSessionId) {
        this(ride, amount);
        this.stripeCheckoutSessionId = stripeCheckoutSessionId;
    }

    public Long getId() { return id; }
    public Ride getRide() { return ride; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public String getStripeCheckoutSessionId() { return stripeCheckoutSessionId; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public void markSucceeded(String paymentIntentId) {
        this.status = "SUCCEEDED";
        this.stripePaymentIntentId = paymentIntentId;
        this.paidAt = Instant.now();
    }
}
