package com.cityride.repository;

import com.cityride.model.RideDecline;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideDeclineRepository extends JpaRepository<RideDecline, Long> {
    boolean existsByRideIdAndDriverId(Long rideId, Long driverId);
}
