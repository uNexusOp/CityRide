package com.cityride.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(unique = true)
    private String googleSubject;

    @Column(nullable = false)
    private boolean available;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private double averageRating;

    @Column(nullable = false)
    private int ratingCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UserAccount() {}

    public UserAccount(String fullName, String email, String passwordHash, Role role) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.available = role == Role.DRIVER;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public String getGoogleSubject() { return googleSubject; }
    public boolean isAvailable() { return available; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public double getAverageRating() { return averageRating; }
    public int getRatingCount() { return ratingCount; }

    public void linkGoogleAccount(String subject) { this.googleSubject = subject; }
    public void setAvailable(boolean available) { this.available = available; }
    public void updateLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    public void updateRating(double averageRating, int ratingCount) {
        this.averageRating = averageRating;
        this.ratingCount = ratingCount;
    }
}
