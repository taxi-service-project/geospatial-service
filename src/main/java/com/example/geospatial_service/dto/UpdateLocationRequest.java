package com.example.geospatial_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateLocationRequest(
        @NotNull
        @Min(value = -180, message = "경도는 -180보다 커야 합니다.")
        @Max(value = 180, message = "경도는 180보다 작아야 합니다.")
        Double longitude,

        @NotNull
        @Min(value = -90, message = "위도는 -90보다 커야 합니다.")
        @Max(value = 90, message = "위도는 90보다 작아야 합니다.")
        Double latitude
) {}