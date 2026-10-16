package com.cityride.api;

import com.cityride.model.PromoCode;
import com.cityride.repository.PromoCodeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/admin/promo-codes")
@PreAuthorize("hasRole('ADMIN')")
public class PromoCodeController {
    private final PromoCodeRepository promotions;

    public PromoCodeController(PromoCodeRepository promotions) {
        this.promotions = promotions;
    }

    record CreatePromoRequest(
            @NotBlank @Size(max = 40) String code,
            @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal discountPercent,
            Instant expiresAt,
            @jakarta.validation.constraints.Positive Integer maxRedemptions) {}
    record PromoResponse(String code, BigDecimal discountPercent, Instant expiresAt,
            Integer maxRedemptions, int redemptionCount, boolean enabled) {
        static PromoResponse from(PromoCode promo) {
            return new PromoResponse(promo.getCode(), promo.getDiscountPercent(), promo.getExpiresAt(),
                    promo.getMaxRedemptions(), promo.getRedemptionCount(), promo.isEnabled());
        }
    }

    @GetMapping
    List<PromoResponse> list() {
        return promotions.findAllByOrderByCodeAsc().stream().map(PromoResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PromoResponse create(@Valid @RequestBody CreatePromoRequest request) {
        if (promotions.findByCodeIgnoreCase(request.code()).isPresent()) {
            throw new com.cityride.service.ApiException(HttpStatus.CONFLICT, "That promo code already exists.");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new com.cityride.service.ApiException(HttpStatus.BAD_REQUEST, "Expiry time must be in the future.");
        }
        return PromoResponse.from(promotions.save(new PromoCode(
                request.code(), request.discountPercent(), request.expiresAt(), request.maxRedemptions())));
    }

    @PatchMapping("/{code}/enabled")
    PromoResponse enable(@PathVariable String code, @RequestBody EnableRequest request) {
        PromoCode promo = promotions.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new com.cityride.service.ApiException(HttpStatus.NOT_FOUND, "Promo code not found."));
        promo.setEnabled(request.enabled());
        return PromoResponse.from(promotions.save(promo));
    }

    record EnableRequest(boolean enabled) {}
}
