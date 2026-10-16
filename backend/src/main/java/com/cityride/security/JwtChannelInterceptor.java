package com.cityride.security;

import com.cityride.model.Role;
import com.cityride.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {
    private final JwtService jwt;
    private final UserRepository users;

    public JwtChannelInterceptor(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        if (accessor.getCommand() == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new AccessDeniedException("A valid bearer token is required.");
            }
            try {
                String email = jwt.getEmail(header.substring(7));
                var user = users.findByEmailIgnoreCase(email)
                        .orElseThrow(() -> new AccessDeniedException("Account not found."));
                accessor.setUser(new UsernamePasswordAuthenticationToken(
                        user.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
            } catch (JwtException | IllegalArgumentException exception) {
                throw new AccessDeniedException("The WebSocket bearer token is invalid.");
            }
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            var user = accessor.getUser();
            String destination = accessor.getDestination();
            if (!(user instanceof Authentication authentication) || destination == null) {
                throw new AccessDeniedException("Authenticated subscriptions are required.");
            }
            if (destination.startsWith("/topic/drivers/")
                    && !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_DRIVER"))) {
                throw new AccessDeniedException("Driver role required.");
            }
            if (destination.startsWith("/topic/admin/")
                    && !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
                throw new AccessDeniedException("Administrator role required.");
            }
            if (!destination.startsWith("/user/") && !destination.startsWith("/topic/drivers/")
                    && !destination.startsWith("/topic/admin/")) {
                throw new AccessDeniedException("This subscription destination is not available.");
            }
        }
        if (accessor.getCommand() == StompCommand.SEND) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null || destination == null
                    || !List.of("/app/driver/location", "/app/driver/availability", "/app/rides/sos")
                            .contains(destination)) {
                throw new AccessDeniedException("This WebSocket action is not available.");
            }
        }
        return message;
    }
}
