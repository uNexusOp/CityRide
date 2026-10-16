package com.cityride.repository;

import com.cityride.model.Ride;
import com.cityride.model.RideStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RideRepository extends JpaRepository<Ride, Long> {
    @Override
    @EntityGraph(attributePaths = {"rider", "driver"})
    Optional<Ride> findById(Long id);

    @EntityGraph(attributePaths = {"rider", "driver"})
    List<Ride> findByRiderIdOrderByCreatedAtDesc(Long riderId);

    @EntityGraph(attributePaths = {"rider", "driver"})
    List<Ride> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    @EntityGraph(attributePaths = {"rider", "driver"})
    List<Ride> findByDriverIdAndStatusInOrderByCreatedAtDesc(Long driverId, List<RideStatus> statuses);

    @EntityGraph(attributePaths = {"rider", "driver"})
    List<Ride> findByStatusOrderByCreatedAtAsc(RideStatus status);

    @EntityGraph(attributePaths = {"rider", "driver"})
    List<Ride> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"rider", "driver"})
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(avg(r.rating), 0) from Ride r where r.driver.id = :driverId and r.rating is not null")
    Double averageRatingForDriver(@Param("driverId") Long driverId);

    @Query("select count(r.rating) from Ride r where r.driver.id = :driverId and r.rating is not null")
    long ratingCountForDriver(@Param("driverId") Long driverId);
}
