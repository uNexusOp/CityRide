package com.cityride.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    private Ride ride;

    @Column(nullable = false, length = 120)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SupportTicket() {}

    public SupportTicket(UserAccount user, Ride ride, String subject, String message) {
        this.user = user;
        this.ride = ride;
        this.subject = subject;
        this.message = message;
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public Ride getRide() { return ride; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(String status) { this.status = status; }
}
