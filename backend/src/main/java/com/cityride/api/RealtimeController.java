package com.cityride.api;

import com.cityride.service.RideService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;
import jakarta.validation.Valid;

@Controller
public class RealtimeController {
    private final RideService rides;

    public RealtimeController(RideService rides) {
        this.rides = rides;
    }

    record DriverLocation(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {}
    record Availability(boolean available) {}
    record SosRequest(@NotNull Long rideId) {}

    @MessageMapping("/driver/location")
    public void location(@Valid @Payload DriverLocation location, Principal principal) {
        if (location.latitude() == null || location.longitude() == null) {
            throw new IllegalArgumentException("Latitude and longitude are required.");
        }
        rides.updateDriverLocation(principal.getName(), location.latitude(), location.longitude());
    }

    @MessageMapping("/driver/availability")
    public void availability(@Payload Availability request, Principal principal) {
        rides.setAvailability(principal.getName(), request.available());
    }

    @MessageMapping("/rides/sos")
    public void sos(@Valid @Payload SosRequest request, Principal principal) {
        if (request.rideId() == null) {
            throw new IllegalArgumentException("Ride ID is required.");
        }
        rides.triggerSos(principal.getName(), request.rideId());
    }
}
