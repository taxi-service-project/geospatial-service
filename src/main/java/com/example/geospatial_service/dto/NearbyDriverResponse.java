package com.example.geospatial_service.dto;

public record NearbyDriverResponse(
        String driverId,
        Double distanceKm
) {}