package com.cityride.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cityride.model.Payment;
import com.cityride.repository.PaymentRepository;
import com.cityride.repository.RideRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cityride-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-characters-long",
        "app.stripe.webhook-secret=whsec_test_cityride_webhook_secret"
})
class RideLifecycleIntegrationTest {
    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RideRepository rides;

    @Autowired
    private PaymentRepository payments;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void riderAndDriverCanCompletePayAndRateARideWithVerifiedWebhook() throws Exception {
        String riderToken = login("rider@cityride.com");
        String driverToken = login("driver@cityride.com");
        JsonNode ride = exchange(HttpMethod.POST, "/api/rides", riderToken,
                Map.of("pickup", "MG Road, Bengaluru", "destination", "Cubbon Park, Bengaluru", "distanceKm", 6),
                HttpStatus.OK);

        assertThat(ride.path("fare").decimalValue()).isEqualByComparingTo("140.00");
        assertThat(ride.has("passwordHash")).isFalse();
        exchange(HttpMethod.GET, "/api/rides/available", riderToken, null, HttpStatus.FORBIDDEN);

        long rideId = ride.path("id").asLong();
        String path = "/api/rides/" + rideId;
        exchange(HttpMethod.POST, path + "/accept", driverToken, null, HttpStatus.OK);
        exchange(HttpMethod.POST, path + "/accept", driverToken, null, HttpStatus.CONFLICT);
        exchange(HttpMethod.POST, path + "/status", riderToken,
                Map.of("status", "IN_PROGRESS"), HttpStatus.CONFLICT);
        exchange(HttpMethod.POST, path + "/status", driverToken,
                Map.of("status", "IN_PROGRESS"), HttpStatus.OK);
        exchange(HttpMethod.POST, path + "/sos", riderToken, null, HttpStatus.ACCEPTED);
        assertThat(exchange(HttpMethod.GET, path, riderToken, null, HttpStatus.OK)
                .path("sosTriggeredAt").isMissingNode()).isFalse();
        exchange(HttpMethod.POST, path + "/status", driverToken,
                Map.of("status", "COMPLETED"), HttpStatus.OK);

        payments.save(new Payment(rides.findById(rideId).orElseThrow(),
                new BigDecimal("140.00"), "cs_test_cityride_1"));
        JsonNode unconfiguredStripe = exchange(HttpMethod.POST, path + "/pay",
                riderToken, null, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unconfiguredStripe.path("message").asText()).contains("STRIPE_SECRET_KEY");
        String webhookPayload = mapper.writeValueAsString(Map.of(
                "id", "evt_cityride_1",
                "object", "event",
                "api_version", "2025-03-31.basil",
                "created", Instant.now().getEpochSecond(),
                "data", Map.of("object", Map.of(
                        "id", "cs_test_cityride_1",
                        "object", "checkout.session",
                        "amount_total", 14000,
                        "currency", "inr",
                        "payment_status", "paid",
                        "metadata", Map.of("rideId", Long.toString(rideId), "riderEmail", "rider@cityride.com"))),
                "livemode", false,
                "pending_webhooks", 1,
                "type", "checkout.session.completed"));
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signedContent = timestamp + "." + webhookPayload;
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec("whsec_test_cityride_webhook_secret"
                .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "t=" + timestamp + ",v1="
                + HexFormat.of().formatHex(hmac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
        HttpHeaders webhookHeaders = new HttpHeaders();
        webhookHeaders.setContentType(MediaType.APPLICATION_JSON);
        webhookHeaders.set("Stripe-Signature", signature);
        ResponseEntity<Void> webhook = rest.exchange("/api/payments/webhook", HttpMethod.POST,
                new HttpEntity<>(webhookPayload, webhookHeaders), Void.class);
        assertThat(webhook.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, path, riderToken, null, HttpStatus.OK)
                .path("paid").asBoolean()).isTrue();
        ResponseEntity<byte[]> receipt = rest.exchange(path + "/receipt", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(riderToken)), byte[].class);
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receipt.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(receipt.getBody()).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        ResponseEntity<byte[]> forbiddenReceipt = rest.exchange(path + "/receipt", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(driverToken)), byte[].class);
        assertThat(forbiddenReceipt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Void> duplicateWebhook = rest.exchange("/api/payments/webhook", HttpMethod.POST,
                new HttpEntity<>(webhookPayload, webhookHeaders), Void.class);
        assertThat(duplicateWebhook.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rating = exchange(HttpMethod.POST, path + "/rating", riderToken,
                Map.of("rating", 5, "review", "A lovely trip"), HttpStatus.OK);
        assertThat(rating.path("rating").asInt()).isEqualTo(5);
        exchange(HttpMethod.POST, path + "/sos", riderToken, null, HttpStatus.CONFLICT);

        JsonNode admin = exchange(HttpMethod.GET, "/api/admin/summary", login("admin@cityride.com"),
                null, HttpStatus.OK);
        assertThat(admin.path("completedRides").asLong()).isEqualTo(1);
        assertThat(admin.path("paidRevenue").decimalValue()).isEqualByComparingTo("140.00");
    }

    @Test
    void driverQueueSupportsDeclineAvailabilityAndRoleRestrictions() {
        String riderToken = login("rider@cityride.com");
        String driverToken = login("driver@cityride.com");
        JsonNode ride = exchange(HttpMethod.POST, "/api/rides", riderToken,
                Map.of("pickup", "Brigade Road, Bengaluru", "destination", "Lalbagh, Bengaluru", "distanceKm", 2),
                HttpStatus.OK);

        JsonNode queue = exchange(HttpMethod.GET, "/api/rides/available", driverToken, null, HttpStatus.OK);
        assertThat(queue.isArray()).isTrue();
        assertThat(queue.findValuesAsText("id")).contains(Long.toString(ride.path("id").asLong()));
        exchange(HttpMethod.POST, "/api/rides/" + ride.path("id").asLong() + "/decline",
                driverToken, null, HttpStatus.NO_CONTENT);
        assertThat(exchange(HttpMethod.GET, "/api/rides/available", driverToken, null, HttpStatus.OK).findValuesAsText("id"))
                .doesNotContain(Long.toString(ride.path("id").asLong()));

        exchange(HttpMethod.POST, "/api/rides/driver/availability",
                driverToken, Map.of("available", false), HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, "/api/rides/available", driverToken, null, HttpStatus.OK).isEmpty())
                .isTrue();
        exchange(HttpMethod.POST, "/api/rides/driver/availability",
                riderToken, Map.of("available", false), HttpStatus.FORBIDDEN);
        exchange(HttpMethod.GET, "/api/drivers/rankings", riderToken, null, HttpStatus.OK);
        exchange(HttpMethod.GET, "/api/drivers/rankings", null, null, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void supportTicketsArePrivateAndVisibleToAdmin() {
        String riderToken = login("rider@cityride.com");
        JsonNode ticket = exchange(HttpMethod.POST, "/api/support/tickets", riderToken,
                Map.of("subject", "Trip question", "message", "Please contact me about this trip."),
                HttpStatus.OK);
        assertThat(ticket.path("status").asText()).isEqualTo("OPEN");
        assertThat(exchange(HttpMethod.GET, "/api/support/tickets/mine", riderToken, null, HttpStatus.OK)
                .findValuesAsText("id")).contains(ticket.path("id").asText());
        exchange(HttpMethod.GET, "/api/support/tickets/admin", riderToken, null, HttpStatus.FORBIDDEN);
        String adminToken = login("admin@cityride.com");
        assertThat(exchange(HttpMethod.GET, "/api/support/tickets/admin", adminToken, null, HttpStatus.OK)
                .findValuesAsText("id")).contains(ticket.path("id").asText());
        exchange(HttpMethod.PATCH, "/api/support/tickets/admin/" + ticket.path("id").asLong(),
                adminToken, Map.of("status", "RESOLVED"), HttpStatus.OK);
    }

    @Test
    void promotionIsAppliedToServerFareAndRedemptionsAreLimited() {
        String riderToken = login("rider@cityride.com");
        JsonNode estimate = exchange(HttpMethod.GET,
                "/api/rides/estimate?distanceKm=10&promoCode=welcome10", riderToken, null, HttpStatus.OK);
        assertThat(estimate.path("subtotal").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(estimate.path("discount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(estimate.path("fare").decimalValue()).isEqualByComparingTo("180.00");

        String adminToken = login("admin@cityride.com");
        exchange(HttpMethod.POST, "/api/admin/promo-codes", adminToken,
                Map.of("code", "ONETIME15", "discountPercent", 15, "maxRedemptions", 1),
                HttpStatus.CREATED);
        JsonNode firstRide = exchange(HttpMethod.POST, "/api/rides", riderToken,
                Map.of("pickup", "Brigade Road, Bengaluru", "destination", "Lalbagh, Bengaluru",
                        "distanceKm", 10, "promoCode", "ONETIME15"),
                HttpStatus.OK);
        assertThat(firstRide.path("promoCode").asText()).isEqualTo("ONETIME15");
        assertThat(firstRide.path("discountAmount").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(firstRide.path("fare").decimalValue()).isEqualByComparingTo("170.00");
        exchange(HttpMethod.POST, "/api/rides", riderToken,
                Map.of("pickup", "Brigade Road, Bengaluru", "destination", "Lalbagh, Bengaluru",
                        "distanceKm", 10, "promoCode", "ONETIME15"),
                HttpStatus.CONFLICT);
        exchange(HttpMethod.POST, "/api/admin/promo-codes", riderToken,
                Map.of("code", "UNAUTHORIZED", "discountPercent", 20), HttpStatus.FORBIDDEN);
        exchange(HttpMethod.GET, "/api/admin/promo-codes", adminToken, null, HttpStatus.OK);
    }

    private String login(String email) {
        JsonNode response = exchange(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", email, "password", "RideDemo2026!"), HttpStatus.OK);
        return response.path("token").asText();
    }

    private JsonNode exchange(
            HttpMethod method, String path, String token, Object body, HttpStatus expectedStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<JsonNode> response = rest.exchange(
                path, method, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        return response.getBody();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
