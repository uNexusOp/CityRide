package com.cityride.api;

import com.cityride.model.Ride;
import com.cityride.model.RideStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record RideResponse(
        Long id,
        String riderName,
        String driverName,
        String pickup,
        String destination,
        BigDecimal distanceKm,
        BigDecimal fare,
        RideStatus status,
        boolean paid,
        Instant createdAt,
        Integer rating,
        String review,
        Double pickupLatitude,
        Double pickupLongitude,
        Double destinationLatitude,
        Double destinationLongitude,
        Double driverLatitude,
        Double driverLongitude,
        double driverAverageRating,
        int driverRatingCount,
        Instant sosTriggeredAt,
        String promoCode,
        BigDecimal discountAmount) {

    public static RideResponse from(Ride ride) {
        return new RideResponse(
                ride.getId(),
                ride.getRider().getFullName(),
                ride.getDriver() == null ? null : ride.getDriver().getFullName(),
                ride.getPickup(),
                ride.getDestination(),
                ride.getDistanceKm(),
                ride.getFare(),
                ride.getStatus(),
                ride.isPaid(),
                ride.getCreatedAt(),
                ride.getRating(),
                ride.getReview(),
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),
                ride.getDestinationLatitude(),
                ride.getDestinationLongitude(),
                ride.getDriver() == null ? null : ride.getDriver().getLatitude(),
                ride.getDriver() == null ? null : ride.getDriver().getLongitude(),
                ride.getDriver() == null ? 0 : ride.getDriver().getAverageRating(),
                ride.getDriver() == null ? 0 : ride.getDriver().getRatingCount(),
                ride.getSosTriggeredAt(),
                ride.getPromoCode(),
                ride.getDiscountAmount());
    }
}
