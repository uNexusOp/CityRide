package com.cityride.api;

import com.cityride.model.Ride;
import com.cityride.model.RideStatus;
import com.cityride.model.Role;
import com.cityride.repository.RideRepository;
import com.cityride.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final RideRepository rides;
    private final UserRepository users;

    public AdminController(RideRepository rides, UserRepository users) {
        this.rides = rides;
        this.users = users;
    }

    @GetMapping("/rides")
    List<RideResponse> allRides() {
        return rides.findAllByOrderByCreatedAtDesc().stream().map(RideResponse::from).toList();
    }

    @GetMapping("/summary")
    Map<String, Object> summary() {
        List<Ride> allRides = rides.findAll();
        long active = allRides.stream()
                .filter(ride -> ride.getStatus() == RideStatus.REQUESTED
                        || ride.getStatus() == RideStatus.ACCEPTED
                        || ride.getStatus() == RideStatus.IN_PROGRESS)
                .count();
        BigDecimal revenue = allRides.stream()
                .filter(Ride::isPaid)
                .map(Ride::getFare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of(
                "totalRides", allRides.size(),
                "activeRides", active,
                "completedRides", allRides.stream()
                        .filter(ride -> ride.getStatus() == RideStatus.COMPLETED).count(),
                "totalRiders", users.countByRole(Role.RIDER),
                "totalDrivers", users.countByRole(Role.DRIVER),
                "paidRevenue", revenue);
    }
}
