package com.cityride.repository;

import com.cityride.model.SupportTicket;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    @Override
    @EntityGraph(attributePaths = {"user", "ride"})
    java.util.Optional<SupportTicket> findById(Long id);

    @EntityGraph(attributePaths = {"user", "ride"})
    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "ride"})
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
