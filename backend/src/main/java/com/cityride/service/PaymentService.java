package com.cityride.service;

import com.cityride.model.Payment;
import com.cityride.model.Ride;
import com.cityride.model.RideStatus;
import com.cityride.repository.PaymentRepository;
import com.cityride.repository.RideRepository;
import com.stripe.exception.StripeException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final RideRepository rides;
    private final PaymentRepository payments;
    private final AuthService auth;
    private final String apiKey;
    private final String webhookSecret;
    private final String frontendUrl;

    public PaymentService(RideRepository rides, PaymentRepository payments, AuthService auth,
            @Value("${app.stripe.secret-key:}") String apiKey,
            @Value("${app.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.rides = rides;
        this.payments = payments;
        this.auth = auth;
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public Map<String, String> createCheckout(String email, Long rideId) {
        if (apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured. Set STRIPE_SECRET_KEY.");
        }
        Ride ride = rides.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found."));
        if (!ride.getRider().getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the rider can pay for this trip.");
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment is available after the ride is completed.");
        }
        if (ride.isPaid()) {
            throw new ApiException(HttpStatus.CONFLICT, "This ride has already been paid.");
        }
        payments.deleteByRideId(rideId);
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(frontendUrl + "/?payment=success&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(frontendUrl + "/?payment=cancelled")
                    .setClientReferenceId(rideId.toString())
                    .putMetadata("rideId", rideId.toString())
                    .putMetadata("riderEmail", ride.getRider().getEmail())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("inr")
                                    .setUnitAmount(ride.getFare().movePointRight(2).longValueExact())
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("CityRide trip " + rideId)
                                            .setDescription(ride.getPickup() + " to " + ride.getDestination())
                                            .build())
                                    .build())
                            .build())
                    .build();
            Session session = Session.create(params, RequestOptions.builder().setApiKey(apiKey).build());
            payments.save(new Payment(ride, ride.getFare(), session.getId()));
            return Map.of("checkoutUrl", session.getUrl());
        } catch (StripeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe could not create a checkout session.");
        }
    }

    @Transactional
    public void handleWebhook(String payload, String signature) {
        if (webhookSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe webhooks are not configured. Set STRIPE_WEBHOOK_SECRET.");
        }
        final Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stripe webhook signature is invalid.");
        }
        if (!"checkout.session.completed".equals(event.getType())) {
            return;
        }
        final StripeObject dataObject;
        try {
            dataObject = event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (EventDataObjectDeserializationException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stripe session payload is invalid.");
        }
        if (!(dataObject instanceof Session session)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stripe session payload is invalid.");
        }
        if (!"paid".equals(session.getPaymentStatus())) {
            return;
        }
        Payment payment = payments.findByStripeCheckoutSessionId(session.getId()).orElse(null);
        if (payment == null) return;
        Ride ride = rides.findByIdForUpdate(payment.getRide().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found."));
        BigDecimal expectedCents = payment.getAmount().movePointRight(2);
        if (session.getAmountTotal() == null
                || expectedCents.longValueExact() != session.getAmountTotal()
                || !"inr".equalsIgnoreCase(session.getCurrency())
                || !ride.getRider().getEmail().equalsIgnoreCase(session.getMetadata().get("riderEmail"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stripe payment does not match the ride amount or rider.");
        }
        if (!ride.isPaid()) {
            payment.markSucceeded(session.getPaymentIntent());
            ride.markPaid();
        }
    }
}
