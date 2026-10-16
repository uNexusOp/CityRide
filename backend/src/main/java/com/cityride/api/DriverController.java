package com.cityride.api;

import com.cityride.service.RideService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {
    private final RideService rides;

    public DriverController(RideService rides) {
        this.rides = rides;
    }

    @GetMapping("/rankings")
    List<Map<String, Object>> rankings() {
        return rides.driverRankings();
    }
}
