package com.cityride.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FareCalculationTest {
    @Test
    void fareEstimateAppliesBaseRateAndMinimumFare() {
        RideService service = new RideService(null, null, null);

        Map<String, Object> shortTrip = service.estimate(new BigDecimal("1.00"), null);
        Map<String, Object> longerTrip = service.estimate(new BigDecimal("10.00"), null);

        assertThat(shortTrip.get("fare")).isEqualTo(new BigDecimal("100.00"));
        assertThat(longerTrip.get("fare")).isEqualTo(new BigDecimal("200.00"));
        assertThat(longerTrip.get("currency")).isEqualTo("INR");
    }
}
