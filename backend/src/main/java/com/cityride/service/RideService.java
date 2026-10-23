package com.cityride.service;

import com.cityride.model.Ride;
import com.cityride.model.RideStatus;
import com.cityride.model.Role;
import com.cityride.model.UserAccount;
import com.cityride.repository.PaymentRepository;
import com.cityride.repository.RideDeclineRepository;
import com.cityride.repository.RideRepository;
import com.cityride.repository.UserRepository;
import com.cityride.repository.PromoCodeRepository;
import com.cityride.model.PromoCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RideService {
    private static final BigDecimal BASE_FARE = new BigDecimal("50.00");
    private static final BigDecimal PER_KM = new BigDecimal("15.00");
    private static final BigDecimal MINIMUM_FARE = new BigDecimal("100.00");

    private final RideRepository rides;
    private final PaymentRepository payments;
    private final AuthService authService;
    private final RideDeclineRepository declines;
    private final UserRepository users;
    private final RideEventPublisher events;
    private final PromoCodeRepository promoCodes;

    public RideService(RideRepository rides, PaymentRepository payments, AuthService authService) {
        this.rides = rides;
        this.payments = payments;
        this.authService = authService;
        this.declines = null;
        this.users = null;
        this.events = null;
        this.promoCodes = null;
    }

    @Autowired
    public RideService(RideRepository rides, PaymentRepository payments, AuthService authService,
            RideDeclineRepository declines, UserRepository users, RideEventPublisher events,
            PromoCodeRepository promoCodes) {
        this.rides = rides;
        this.payments = payments;
        this.authService = authService;
        this.declines = declines;
        this.users = users;
        this.events = events;
        this.promoCodes = promoCodes;
    }

    @Transactional
    public Ride create(String email, String pickup, String destination, BigDecimal distanceKm,
            Double pickupLatitude, Double pickupLongitude, Double destinationLatitude, Double destinationLongitude,
            String promoCode) {
        UserAccount rider = requireRole(email, Role.RIDER);
        BigDecimal subtotal = BASE_FARE.add(PER_KM.multiply(distanceKm)).max(MINIMUM_FARE)
                .setScale(2, RoundingMode.HALF_UP);
        PromoCode promotion = promoCode == null || promoCode.isBlank() ? null
                : promoCodes.findByCodeForUpdate(promoCode.trim())
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Promo code is invalid."));
        if (promotion != null && !promotion.isValidAt(java.time.Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Promo code is expired or has reached its redemption limit.");
        }
        BigDecimal discount = promotion == null ? BigDecimal.ZERO
                : subtotal.multiply(promotion.getDiscountPercent()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal fare = subtotal.subtract(discount).max(new BigDecimal("0.50"));
        Ride ride = new Ride(rider, pickup.trim(), destination.trim(),
                distanceKm.setScale(2, RoundingMode.HALF_UP), fare);
        ride.setCoordinates(pickupLatitude, pickupLongitude, destinationLatitude, destinationLongitude);
        if (promotion != null) {
            promotion.redeem();
            ride.setPromotion(promotion.getCode(), discount);
        }
        Ride saved = rides.save(ride);
        events.rideRequested(saved);
        return saved;
    }

    public List<Ride> mine(String email) {
        UserAccount user = authService.requireUser(email);
        return user.getRole() == Role.DRIVER
                ? rides.findByDriverIdOrderByCreatedAtDesc(user.getId())
                : rides.findByRiderIdOrderByCreatedAtDesc(user.getId());
    }

    public List<Ride> available(String email) {
        UserAccount driver = requireRole(email, Role.DRIVER);
        if (!driver.isAvailable()) {
            return List.of();
        }
        return rides.findByStatusOrderByCreatedAtAsc(RideStatus.REQUESTED).stream()
                .filter(ride -> !declines.existsByRideIdAndDriverId(ride.getId(), driver.getId()))
                .filter(ride -> isNearby(ride, driver))
                .toList();
    }

    @Transactional
    public Ride accept(String email, Long rideId) {
        UserAccount driver = requireRole(email, Role.DRIVER);
        Ride ride = rides.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found."));
        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This ride is no longer available.");
        }
        ride.accept(driver);
        events.rideChanged(ride);
        return ride;
    }

    @Transactional
    public void decline(String email, Long rideId) {
        UserAccount driver = requireRole(email, Role.DRIVER);
        Ride ride = requireRideForUpdate(rideId);
        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This ride is no longer available.");
        }
        if (!declines.existsByRideIdAndDriverId(rideId, driver.getId())) {
            declines.save(new com.cityride.model.RideDecline(ride, driver));
        }
    }

    public UserAccount setAvailability(String email, boolean available) {
        UserAccount driver = requireRole(email, Role.DRIVER);
        driver.setAvailable(available);
        return users.save(driver);
    }

    public UserAccount updateDriverLocation(String email, double latitude, double longitude) {
        UserAccount driver = requireRole(email, Role.DRIVER);
        driver.updateLocation(latitude, longitude);
        UserAccount saved = users.save(driver);
        rides.findByDriverIdAndStatusInOrderByCreatedAtDesc(driver.getId(),
                List.of(RideStatus.ACCEPTED, RideStatus.ARRIVED, RideStatus.IN_PROGRESS))
                .forEach(ride -> {
                    if (ride.getStatus() == RideStatus.ACCEPTED
                            && ride.getPickupLatitude() != null && ride.getPickupLongitude() != null
                            && isNearPickup(latitude, longitude, ride.getPickupLatitude(), ride.getPickupLongitude())) {
                        ride.setStatus(RideStatus.ARRIVED);
                        rides.save(ride);
                        events.rideChanged(ride);
                    }
                    events.driverLocation(ride, latitude, longitude);
                });
        return saved;
    }

    private boolean isNearPickup(double latitude, double longitude, double pickupLatitude, double pickupLongitude) {
        final double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(latitude - pickupLatitude);
        double lonDistance = Math.toRadians(longitude - pickupLongitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(pickupLatitude)) * Math.cos(Math.toRadians(latitude))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = earthRadiusKm * c;
        return distanceKm <= 0.2;
    }

    public List<Map<String, Object>> driverRankings() {
        return users.findByRoleOrderByAverageRatingDescFullNameAsc(Role.DRIVER).stream()
                .map(driver -> {
                    Map<String, Object> ranking = new HashMap<>();
                    ranking.put("driverId", driver.getId());
                    ranking.put("driverName", driver.getFullName());
                    ranking.put("averageRating", driver.getAverageRating());
                    ranking.put("ratingCount", driver.getRatingCount());
                    ranking.put("completedRides", rides.findByDriverIdOrderByCreatedAtDesc(driver.getId()).stream()
                            .filter(ride -> ride.getStatus() == RideStatus.COMPLETED).count());
                    return ranking;
                })
                .toList();
    }

    @Transactional
    public Ride updateStatus(String email, Long rideId, RideStatus next) {
        UserAccount user = authService.requireUser(email);
        Ride ride = requireRideForUpdate(rideId);
        boolean rider = ride.getRider().getId().equals(user.getId());
        boolean driver = ride.getDriver() != null && ride.getDriver().getId().equals(user.getId());
        if (!rider && !driver) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this ride.");
        }
        boolean allowed = switch (next) {
            case ARRIVED -> driver && ride.getStatus() == RideStatus.ACCEPTED;
            case IN_PROGRESS -> driver && (ride.getStatus() == RideStatus.ACCEPTED || ride.getStatus() == RideStatus.ARRIVED);
            case COMPLETED -> driver && ride.getStatus() == RideStatus.IN_PROGRESS;
            case CANCELLED -> (rider || driver)
                    && (ride.getStatus() == RideStatus.REQUESTED || ride.getStatus() == RideStatus.ACCEPTED);
            default -> false;
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.CONFLICT, "That ride status change is not allowed.");
        }
        ride.setStatus(next);
        events.rideChanged(ride);
        return ride;
    }

    @Transactional
    public Ride rate(String email, Long rideId, int rating, String review) {
        Ride ride = requireRideForUpdate(rideId);
        if (!ride.getRider().getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the rider can rate this trip.");
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "You can rate a trip after it is completed.");
        }
        if (ride.getRating() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "This trip has already been rated.");
        }
        ride.setRating(rating, review == null ? "" : review.trim());
        UserAccount driver = ride.getDriver();
        if (driver != null) {
            driver.updateRating(rides.averageRatingForDriver(driver.getId()),
                    Math.toIntExact(rides.ratingCountForDriver(driver.getId())));
            users.save(driver);
        }
        events.rideChanged(ride);
        return ride;
    }

    @Transactional
    public void triggerSos(String email, Long rideId) {
        Ride ride = requireRideForUpdate(rideId);
        getAccessible(email, rideId);
        if (ride.getStatus() != RideStatus.ACCEPTED && ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "SOS is available during an accepted or active ride.");
        }
        ride.triggerSos();
        events.rideChanged(ride);
    }

    public Ride getAccessible(String email, Long rideId) {
        UserAccount user = authService.requireUser(email);
        Ride ride = requireRide(rideId);
        if (!ride.getRider().getId().equals(user.getId())
                && (ride.getDriver() == null || !ride.getDriver().getId().equals(user.getId()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this ride.");
        }
        return ride;
    }

    public Map<String, Object> estimate(BigDecimal distanceKm, String promoCode) {
        BigDecimal subtotal = BASE_FARE.add(PER_KM.multiply(distanceKm)).max(MINIMUM_FARE)
                .setScale(2, RoundingMode.HALF_UP);
        PromoCode promotion = promoCode == null || promoCode.isBlank() ? null
                : promoCodes.findByCodeIgnoreCase(promoCode.trim())
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Promo code is invalid."));
        if (promotion != null && !promotion.isValidAt(java.time.Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Promo code is expired or has reached its redemption limit.");
        }
        BigDecimal discount = promotion == null ? BigDecimal.ZERO
                : subtotal.multiply(promotion.getDiscountPercent()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal fare = subtotal.subtract(discount).max(new BigDecimal("0.50"));
        Map<String, Object> result = new HashMap<>();
        result.put("distanceKm", distanceKm);
        result.put("subtotal", subtotal);
        result.put("discount", discount);
        result.put("fare", fare);
        result.put("currency", "INR");
        result.put("promoCode", promotion == null ? null : promotion.getCode());
        return result;
    }

    private UserAccount requireRole(String email, Role role) {
        UserAccount user = authService.requireUser(email);
        if (user.getRole() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This action is not available for your account.");
        }
        return user;
    }

    private boolean isNearby(Ride ride, UserAccount driver) {
        if (ride.getPickupLatitude() == null || driver.getLatitude() == null || driver.getLongitude() == null) {
            return true;
        }
        double latitudeDelta = Math.toRadians(ride.getPickupLatitude() - driver.getLatitude());
        double longitudeDelta = Math.toRadians(ride.getPickupLongitude() - driver.getLongitude());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(driver.getLatitude())) * Math.cos(Math.toRadians(ride.getPickupLatitude()))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double distance = 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return distance <= 25;
    }

    private Ride requireRide(Long id) {
        return rides.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found."));
    }

    private Ride requireRideForUpdate(Long id) {
        return rides.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found."));
    }
}
