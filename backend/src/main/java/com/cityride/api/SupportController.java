package com.cityride.api;

import com.cityride.model.SupportTicket;
import com.cityride.repository.SupportTicketRepository;
import com.cityride.service.ApiException;
import com.cityride.service.AuthService;
import com.cityride.service.RideService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/support/tickets")
public class SupportController {
    private final SupportTicketRepository tickets;
    private final AuthService auth;
    private final RideService rides;

    public SupportController(SupportTicketRepository tickets, AuthService auth, RideService rides) {
        this.tickets = tickets;
        this.auth = auth;
        this.rides = rides;
    }

    record CreateTicketRequest(
            Long rideId,
            @NotBlank @Size(max = 120) String subject,
            @NotBlank @Size(max = 2000) String message) {}
    record TicketStatusRequest(@NotBlank String status) {}
    record TicketResponse(Long id, String userName, String userEmail, Long rideId,
            String subject, String message, String status, Instant createdAt) {
        static TicketResponse from(SupportTicket ticket) {
            return new TicketResponse(ticket.getId(), ticket.getUser().getFullName(),
                    ticket.getUser().getEmail(), ticket.getRide() == null ? null : ticket.getRide().getId(),
                    ticket.getSubject(), ticket.getMessage(), ticket.getStatus(), ticket.getCreatedAt());
        }
    }

    @PostMapping
    TicketResponse create(@Valid @RequestBody CreateTicketRequest request, Authentication authentication) {
        var user = auth.requireUser(authentication.getName());
        var ride = request.rideId() == null ? null : rides.getAccessible(authentication.getName(), request.rideId());
        return TicketResponse.from(tickets.save(
                new SupportTicket(user, ride, request.subject().trim(), request.message().trim())));
    }

    @GetMapping("/mine")
    List<TicketResponse> mine(Authentication authentication) {
        var user = auth.requireUser(authentication.getName());
        return tickets.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(TicketResponse::from).toList();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    List<TicketResponse> all() {
        return tickets.findAllByOrderByCreatedAtDesc().stream().map(TicketResponse::from).toList();
    }

    @PatchMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    TicketResponse updateStatus(@PathVariable Long id, @Valid @RequestBody TicketStatusRequest request) {
        if (!List.of("OPEN", "IN_PROGRESS", "RESOLVED").contains(request.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ticket status must be OPEN, IN_PROGRESS, or RESOLVED.");
        }
        SupportTicket ticket = tickets.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket not found."));
        ticket.setStatus(request.status());
        return TicketResponse.from(tickets.save(ticket));
    }
}
