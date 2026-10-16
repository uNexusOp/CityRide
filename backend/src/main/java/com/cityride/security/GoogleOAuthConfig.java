package com.cityride.security;

import com.cityride.service.AuthService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@ConditionalOnProperty(name = "app.oauth.google.enabled", havingValue = "true")
public class GoogleOAuthConfig {
    @Bean
    ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${app.oauth.google.client-id}") String clientId,
            @Value("${app.oauth.google.client-secret}") String clientSecret) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth is enabled but GOOGLE_CLIENT_ID or GOOGLE_CLIENT_SECRET is missing.");
        }
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName("Google")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email", "profile")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    AuthenticationSuccessHandler googleAuthenticationSuccessHandler(
            AuthService auth, @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        return (request, response, authentication) -> {
            OAuth2User principal = (OAuth2User) authentication.getPrincipal();
            String subject = principal.getAttribute("sub");
            String email = principal.getAttribute("email");
            String fullName = principal.getAttribute("name");
            if (subject == null || email == null || fullName == null
                    || !Boolean.TRUE.equals(principal.getAttribute("email_verified"))) {
                response.sendError(401, "Google did not return a verified email address.");
                return;
            }
            String token = (String) auth.loginWithGoogle(subject, email, fullName).get("token");
            response.sendRedirect(frontendUrl + "/#oauth_token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8));
        };
    }
}
