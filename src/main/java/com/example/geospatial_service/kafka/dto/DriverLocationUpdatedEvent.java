package com.example.geospatial_service.kafka.dto;

public record DriverLocationUpdatedEvent(
        String driverId,
        double latitude,
        double longitude
) {}