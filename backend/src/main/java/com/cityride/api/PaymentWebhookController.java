package com.cityride.api;

import com.cityride.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentWebhookController {
    private final PaymentService payments;

    public PaymentWebhookController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/api/payments/webhook")
    ResponseEntity<Void> webhook(
            @RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        payments.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
