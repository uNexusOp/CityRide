package com.cityride.repository;

import com.cityride.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByRideId(Long rideId);
    java.util.Optional<Payment> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    java.util.Optional<Payment> findByRideId(Long rideId);
    void deleteByRideId(Long rideId);
}
