package com.cityride.config;

import com.cityride.model.Role;
import com.cityride.model.UserAccount;
import com.cityride.repository.UserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final boolean production;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwords,
            @Value("${INITIAL_ADMIN_EMAIL:}") String email,
            @Value("${INITIAL_ADMIN_PASSWORD:}") String password,
            @Value("${spring.profiles.active:}") String activeProfile) {
        this.users = users;
        this.passwords = passwords;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
        this.production = java.util.Arrays.stream(activeProfile.split(","))
                .anyMatch("production"::equalsIgnoreCase);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() && password.isEmpty()) {
            if (production && users.countByRole(Role.ADMIN) == 0) {
                throw new IllegalStateException(
                        "No administrator exists. Set INITIAL_ADMIN_EMAIL and INITIAL_ADMIN_PASSWORD.");
            }
            return;
        }
        if (email.isBlank() || password.length() < 12 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException(
                    "Set a valid INITIAL_ADMIN_EMAIL and INITIAL_ADMIN_PASSWORD of at least 12 characters.");
        }
        users.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ADMIN) {
                throw new IllegalStateException("INITIAL_ADMIN_EMAIL belongs to a non-administrator account.");
            }
        }, () -> users.save(new UserAccount(
                "CityRide Administrator", email, passwords.encode(password), Role.ADMIN)));
    }
}
