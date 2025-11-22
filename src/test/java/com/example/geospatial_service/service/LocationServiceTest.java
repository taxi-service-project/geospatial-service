package com.example.geospatial_service.service;

import com.example.geospatial_service.dto.NearbyDriverResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveGeoOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @InjectMocks
    private LocationService locationService;

    @Mock
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Mock
    private ReactiveGeoOperations<String, String> reactiveGeoOperations;

    @Test
    @DisplayName("주변 기사 검색이 성공하고, 결과가 거리순으로 정렬되어 km 단위로 반환되어야 한다")
    void findNearbyDrivers_Success() {
        // given
        double lon = 127.0, lat = 37.5;
        int radius = 5;

        var result1 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("driver:101", new Point(127.01, 37.51)),
                new Distance(1.5, Metrics.KILOMETERS) // 1.5km
        );
        var result2 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("driver:102", new Point(127.02, 37.52)),
                new Distance(2.5, Metrics.KILOMETERS) // 2.5km
        );

        when(reactiveRedisTemplate.opsForGeo()).thenReturn(reactiveGeoOperations);
        when(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(Flux.just(result1, result2));

        // when
        Flux<NearbyDriverResponse> resultFlux = locationService.findNearbyDrivers(lon, lat, radius);

        // then
        StepVerifier.create(resultFlux)
                    .assertNext(driver -> {
                        assertThat(driver.driverId()).isEqualTo("101");
                        assertThat(driver.distanceKm()).isEqualTo(1.5);
                    })
                    .assertNext(driver -> {
                        assertThat(driver.driverId()).isEqualTo("102");
                        assertThat(driver.distanceKm()).isEqualTo(2.5);
                    })
                    .verifyComplete();
    }

    @Test
    @DisplayName("주변에 검색된 기사가 없을 경우, 비어있는 Flux를 반환해야 한다")
    void findNearbyDrivers_ReturnsEmpty_WhenNoDriversFound() {
        // given
        double lon = 127.0, lat = 37.5;
        int radius = 5;

        when(reactiveRedisTemplate.opsForGeo()).thenReturn(reactiveGeoOperations);
        when(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(Flux.empty());

        // when
        Flux<NearbyDriverResponse> resultFlux = locationService.findNearbyDrivers(lon, lat, radius);

        // then
        StepVerifier.create(resultFlux)
                    .expectNextCount(0)
                    .verifyComplete();
    }
}