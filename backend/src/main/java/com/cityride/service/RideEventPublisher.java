package com.cityride.service;

import com.cityride.model.Ride;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RideEventPublisher {
    private final SimpMessagingTemplate messaging;

    public RideEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void rideRequested(Ride ride) {
        messaging.convertAndSend("/topic/drivers/requests", Map.of("type", "RIDE_REQUESTED", "rideId", ride.getId()));
    }

    public void rideChanged(Ride ride) {
        Map<String, Object> event = Map.of(
                "type", "RIDE_UPDATED",
                "rideId", ride.getId(),
                "status", ride.getStatus().name(),
                "sosTriggered", ride.getSosTriggeredAt() != null);
        messaging.convertAndSendToUser(ride.getRider().getEmail(), "/queue/ride-events", event);
        if (ride.getDriver() != null) {
            messaging.convertAndSendToUser(ride.getDriver().getEmail(), "/queue/ride-events", event);
        }
        if (ride.getSosTriggeredAt() != null) {
            messaging.convertAndSend("/topic/admin/sos", Map.of(
                    "type", "SOS_ALERT", "rideId", ride.getId(),
                    "riderName", ride.getRider().getFullName(),
                    "pickup", ride.getPickup(), "destination", ride.getDestination()));
        }
    }

    public void driverLocation(Ride ride, double latitude, double longitude) {
        if (ride.getRider() != null) {
            messaging.convertAndSendToUser(ride.getRider().getEmail(), "/queue/ride-events", Map.of(
                    "type", "DRIVER_LOCATION",
                    "rideId", ride.getId(),
                    "latitude", latitude,
                    "longitude", longitude));
        }
    }
}
