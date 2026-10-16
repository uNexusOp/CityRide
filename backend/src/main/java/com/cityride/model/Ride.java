package com.cityride.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rides")
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount rider;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount driver;

    @Column(nullable = false)
    private String pickup;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal distanceKm;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal fare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status = RideStatus.REQUESTED;

    @Column(nullable = false)
    private boolean paid = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Integer rating;

    @Column(length = 500)
    private String review;

    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Instant sosTriggeredAt;

    @Column(length = 40)
    private String promoCode;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    protected Ride() {}

    public Ride(UserAccount rider, String pickup, String destination, BigDecimal distanceKm, BigDecimal fare) {
        this.rider = rider;
        this.pickup = pickup;
        this.destination = destination;
        this.distanceKm = distanceKm;
        this.fare = fare;
    }

    public Long getId() { return id; }
    public UserAccount getRider() { return rider; }
    public UserAccount getDriver() { return driver; }
    public String getPickup() { return pickup; }
    public String getDestination() { return destination; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public BigDecimal getFare() { return fare; }
    public RideStatus getStatus() { return status; }
    public boolean isPaid() { return paid; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getRating() { return rating; }
    public String getReview() { return review; }
    public Double getPickupLatitude() { return pickupLatitude; }
    public Double getPickupLongitude() { return pickupLongitude; }
    public Double getDestinationLatitude() { return destinationLatitude; }
    public Double getDestinationLongitude() { return destinationLongitude; }
    public Instant getSosTriggeredAt() { return sosTriggeredAt; }
    public String getPromoCode() { return promoCode; }
    public BigDecimal getDiscountAmount() { return discountAmount; }

    public void accept(UserAccount driver) {
        this.driver = driver;
        this.status = RideStatus.ACCEPTED;
    }

    public void setStatus(RideStatus status) { this.status = status; }
    public void markPaid() { this.paid = true; }
    public void setRating(Integer rating, String review) {
        this.rating = rating;
        this.review = review;
    }
    public void setCoordinates(Double pickupLatitude, Double pickupLongitude,
            Double destinationLatitude, Double destinationLongitude) {
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
    }
    public void triggerSos() { this.sosTriggeredAt = Instant.now(); }
    public void setPromotion(String promoCode, BigDecimal discountAmount) {
        this.promoCode = promoCode;
        this.discountAmount = discountAmount;
    }
}
