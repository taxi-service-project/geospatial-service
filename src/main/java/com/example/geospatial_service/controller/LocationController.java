package com.example.geospatial_service.controller;

import com.example.geospatial_service.dto.NearbyDriverResponse;
import com.example.geospatial_service.service.LocationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/search")
    public Flux<NearbyDriverResponse> searchNearbyDrivers(
            @RequestParam @Min(-180) @Max(180) Double longitude,
            @RequestParam @Min(-90) @Max(90) Double latitude,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int radius) {
        return locationService.findNearbyDrivers(longitude, latitude, radius);
    }
}