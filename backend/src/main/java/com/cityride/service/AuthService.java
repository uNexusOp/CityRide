package com.cityride.service;

import com.cityride.model.Role;
import com.cityride.model.UserAccount;
import com.cityride.repository.UserRepository;
import com.cityride.security.JwtService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<String, Object> register(String fullName, String email, String password, Role role) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        UserAccount user = users.save(new UserAccount(
                fullName.trim(), email.trim().toLowerCase(), passwordEncoder.encode(password), role));
        return session(user);
    }

    public Map<String, Object> login(String email, String password) {
        UserAccount user = users.findByEmailIgnoreCase(email.trim())
                .filter(account -> account.getPasswordHash() != null
                        && passwordEncoder.matches(password, account.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."));
        return session(user);
    }

    public Map<String, Object> loginWithGoogle(String subject, String email, String fullName) {
        UserAccount user = users.findByGoogleSubject(subject).orElseGet(() -> {
            UserAccount account = users.findByEmailIgnoreCase(email)
                    .orElseGet(() -> users.save(new UserAccount(
                            fullName.trim(), email.trim().toLowerCase(),
                            passwordEncoder.encode(UUID.randomUUID().toString()), Role.RIDER)));
            if (account.getRole() == Role.ADMIN) {
                throw new ApiException(HttpStatus.FORBIDDEN,
                        "Administrator accounts cannot be linked to social sign-in.");
            }
            if (account.getGoogleSubject() != null && !account.getGoogleSubject().equals(subject)) {
                throw new ApiException(HttpStatus.CONFLICT, "This email is linked to a different Google account.");
            }
            account.linkGoogleAccount(subject);
            return users.save(account);
        });
        return session(user);
    }

    public UserAccount requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
    }

    private Map<String, Object> session(UserAccount user) {
        return Map.of("token", jwtService.createToken(user), "user", publicUser(user));
    }

    public static Map<String, Object> publicUser(UserAccount user) {
        return Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "available", user.isAvailable(),
                "averageRating", user.getAverageRating(),
                "ratingCount", user.getRatingCount());
    }
}
