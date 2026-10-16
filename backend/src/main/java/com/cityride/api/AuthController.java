package com.cityride.api;

import com.cityride.model.Role;
import com.cityride.model.UserAccount;
import com.cityride.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    record RegisterRequest(
            @NotBlank(message = "Please enter your name.") @Size(max = 100) String fullName,
            @NotBlank(message = "Please enter a valid email.") @Email String email,
            @NotBlank(message = "Password must be at least 8 characters.") @Size(min = 8) String password,
            Role role) {}

    record LoginRequest(
            @NotBlank(message = "Please enter your email.") @Email String email,
            @NotBlank(message = "Please enter your password.") String password) {}

    @PostMapping("/register")
    Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        Role role = request.role() == null ? Role.RIDER : request.role();
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Admin accounts cannot be created through registration.");
        }
        return authService.register(request.fullName(), request.email(), request.password(), role);
    }

    @PostMapping("/login")
    Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @GetMapping("/me")
    Map<String, Object> me(Authentication authentication) {
        UserAccount user = authService.requireUser(authentication.getName());
        return AuthService.publicUser(user);
    }
}
