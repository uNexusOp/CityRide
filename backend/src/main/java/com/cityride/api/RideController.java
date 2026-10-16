package com.cityride.api;

import com.cityride.model.Ride;
import com.cityride.model.RideStatus;
import com.cityride.service.RideService;
import com.cityride.service.PaymentService;
import com.cityride.service.ReceiptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@RequestMapping("/api/rides")
public class RideController {
    private final RideService rideService;
    private final PaymentService paymentService;
    private final ReceiptService receiptService;

    public RideController(RideService rideService, PaymentService paymentService, ReceiptService receiptService) {
        this.rideService = rideService;
        this.paymentService = paymentService;
        this.receiptService = receiptService;
    }

    record CreateRideRequest(
            @NotBlank(message = "Enter a pickup location.") @Size(max = 180) String pickup,
            @NotBlank(message = "Enter a destination.") @Size(max = 180) String destination,
            @DecimalMin(value = "0.5", message = "Distance must be at least 0.5 km.")
            @DecimalMax(value = "500", message = "Distance must be 500 km or less.")
            @NotNull BigDecimal distanceKm,
            @DecimalMin("-90.0") @DecimalMax("90.0")
            Double pickupLatitude,
            @DecimalMin("-180.0") @DecimalMax("180.0")
            Double pickupLongitude,
            @DecimalMin("-90.0") @DecimalMax("90.0")
            Double destinationLatitude,
            @DecimalMin("-180.0") @DecimalMax("180.0")
            Double destinationLongitude,
            @Size(max = 40) String promoCode) {}

    record StatusRequest(@jakarta.validation.constraints.NotNull(message = "Choose a ride status.") RideStatus status) {}
    record RatingRequest(
            @jakarta.validation.constraints.Min(value = 1, message = "Rating must be between 1 and 5.")
            @jakarta.validation.constraints.Max(value = 5, message = "Rating must be between 1 and 5.")
            int rating,
            @Size(max = 500) String review) {}

    @PostMapping
    RideResponse create(@Valid @RequestBody CreateRideRequest request, Authentication authentication) {
        if (request.distanceKm() == null) {
            throw new IllegalArgumentException("Enter the approximate trip distance.");
        }
        boolean anyCoordinate = request.pickupLatitude() != null || request.pickupLongitude() != null
                || request.destinationLatitude() != null || request.destinationLongitude() != null;
        boolean allCoordinates = request.pickupLatitude() != null && request.pickupLongitude() != null
                && request.destinationLatitude() != null && request.destinationLongitude() != null;
        if (anyCoordinate && !allCoordinates) {
            throw new IllegalArgumentException("Provide both coordinates for the pickup and destination.");
        }
        return RideResponse.from(rideService.create(
                authentication.getName(), request.pickup(), request.destination(), request.distanceKm(),
                request.pickupLatitude(), request.pickupLongitude(),
                request.destinationLatitude(), request.destinationLongitude(), request.promoCode()));
    }

    @GetMapping("/mine")
    List<RideResponse> mine(Authentication authentication) {
        return rideService.mine(authentication.getName()).stream().map(RideResponse::from).toList();
    }

    @GetMapping("/available")
    List<RideResponse> available(Authentication authentication) {
        return rideService.available(authentication.getName()).stream().map(RideResponse::from).toList();
    }

    @GetMapping("/{id}")
    RideResponse get(@PathVariable Long id, Authentication authentication) {
        return RideResponse.from(rideService.getAccessible(authentication.getName(), id));
    }

    @PostMapping("/{id}/accept")
    RideResponse accept(@PathVariable Long id, Authentication authentication) {
        return RideResponse.from(rideService.accept(authentication.getName(), id));
    }

    @PostMapping("/{id}/decline")
    ResponseEntity<Void> decline(@PathVariable Long id, Authentication authentication) {
        rideService.decline(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/driver/availability")
    Map<String, Boolean> availability(@RequestBody AvailabilityRequest request, Authentication authentication) {
        return Map.of("available",
                rideService.setAvailability(authentication.getName(), request.available()).isAvailable());
    }

    @PostMapping("/{id}/status")
    RideResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody StatusRequest request, Authentication authentication) {
        return RideResponse.from(rideService.updateStatus(authentication.getName(), id, request.status()));
    }

    @PostMapping("/{id}/pay")
    Map<String, String> pay(@PathVariable Long id, Authentication authentication) {
        return paymentService.createCheckout(authentication.getName(), id);
    }

    @GetMapping("/{id}/receipt")
    ResponseEntity<byte[]> receipt(@PathVariable Long id, Authentication authentication) {
        byte[] pdf = receiptService.pdf(authentication.getName(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cityride-receipt-" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/{id}/receipt/email")
    ResponseEntity<Void> emailReceipt(@PathVariable Long id, Authentication authentication) {
        receiptService.email(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/sos")
    ResponseEntity<Void> sos(@PathVariable Long id, Authentication authentication) {
        rideService.triggerSos(authentication.getName(), id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/rating")
    RideResponse rate(
            @PathVariable Long id, @Valid @RequestBody RatingRequest request, Authentication authentication) {
        return RideResponse.from(rideService.rate(
                authentication.getName(), id, request.rating(), request.review()));
    }

    @GetMapping("/estimate")
    Map<String, Object> estimate(
            @RequestParam @DecimalMin("0.5") @DecimalMax("500") BigDecimal distanceKm,
            @RequestParam(required = false) @Size(max = 40) String promoCode) {
        return rideService.estimate(distanceKm, promoCode);
    }

    record AvailabilityRequest(boolean available) {}
}
