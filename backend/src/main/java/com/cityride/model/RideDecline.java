package com.cityride.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ride_declines", uniqueConstraints = @UniqueConstraint(columnNames = {"ride_id", "driver_id"}))
public class RideDecline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Ride ride;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount driver;

    protected RideDecline() {}

    public RideDecline(Ride ride, UserAccount driver) {
        this.ride = ride;
        this.driver = driver;
    }
}
