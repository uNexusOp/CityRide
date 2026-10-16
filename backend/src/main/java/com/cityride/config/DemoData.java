package com.cityride.config;

import com.cityride.model.Role;
import com.cityride.model.UserAccount;
import com.cityride.repository.UserRepository;
import com.cityride.repository.PromoCodeRepository;
import com.cityride.model.PromoCode;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("!production")
public class DemoData {
    @Bean
    CommandLineRunner seedDemoUsers(UserRepository users, PasswordEncoder encoder, PromoCodeRepository promoCodes) {
        return args -> {
            createDemoUser(users, encoder, "Alex Morgan", "rider@cityride.com", Role.RIDER);
            createDemoUser(users, encoder, "Jordan Lee", "driver@cityride.com", Role.DRIVER);
            createDemoUser(users, encoder, "Taylor Quinn", "admin@cityride.com", Role.ADMIN);
            if (promoCodes.findByCodeIgnoreCase("WELCOME10").isEmpty()) {
                promoCodes.save(new PromoCode("WELCOME10", new BigDecimal("10.00"), null, null));
            }
        };
    }

    private void createDemoUser(
            UserRepository users, PasswordEncoder encoder, String name, String email, Role role) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new UserAccount(name, email, encoder.encode("RideDemo2026!"), role));
        }
    }
}
