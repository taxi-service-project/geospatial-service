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
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @InjectMocks
    private LocationService locationService;

    @Mock
    private ReactiveRedisTemplate<String, String> cacheRedisTemplate;

    @Mock
    private ReactiveRedisTemplate<String, String> storageRedisTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ReactiveGeoOperations<String, String> reactiveGeoOperations;

    @Test
    @DisplayName("주변 기사 검색 시, '생존 신고(Active Key)'가 있는 기사만 반환해야 한다")
    void findNearbyDrivers_Success_OnlyActiveDrivers() {
        // given
        double lon = 127.0, lat = 37.5;
        int radius = 5;

        var result1 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("driver:101", new Point(127.01, 37.51)),
                new Distance(1.5, Metrics.KILOMETERS)
        );
        var result2 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("driver:102", new Point(127.02, 37.52)),
                new Distance(2.5, Metrics.KILOMETERS)
        );

        when(cacheRedisTemplate.opsForGeo()).thenReturn(reactiveGeoOperations);
        when(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(Flux.just(result1, result2));

        when(storageRedisTemplate.hasKey("driver_active:101")).thenReturn(Mono.just(true));
        when(storageRedisTemplate.hasKey("driver_active:102")).thenReturn(Mono.just(true));

        // when
        Flux<NearbyDriverResponse> resultFlux = locationService.findNearbyDrivers(lon, lat, radius);

        // then
        StepVerifier.create(resultFlux)
                    .assertNext(driver -> {
                        assertThat(driver.driverId()).isEqualTo("101");
                    })
                    .assertNext(driver -> {
                        assertThat(driver.driverId()).isEqualTo("102");
                    })
                    .verifyComplete();
    }

    @Test
    @DisplayName("생존 신고(Active Key)가 만료된 '유령 기사'는 결과에서 제외되고 삭제되어야 한다")
    void findNearbyDrivers_LazyEviction_ZombieDriverRemoved() {
        // given
        double lon = 127.0, lat = 37.5;
        int radius = 5;

        var activeDriver = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("driver:101", new Point(127.01, 37.51)),
                new Distance(1.0, Metrics.KILOMETERS)
        );
        var zombieDriver = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("driver:999", new Point(127.02, 37.52)),
                new Distance(2.0, Metrics.KILOMETERS)
        );

        when(cacheRedisTemplate.opsForGeo()).thenReturn(reactiveGeoOperations);
        when(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(Flux.just(activeDriver, zombieDriver));

        when(storageRedisTemplate.hasKey("driver_active:101")).thenReturn(Mono.just(true));
        when(storageRedisTemplate.hasKey("driver_active:999")).thenReturn(Mono.just(false));

        when(reactiveGeoOperations.remove(anyString(), eq("driver:999"))).thenReturn(Mono.just(1L));

        // when
        Flux<NearbyDriverResponse> resultFlux = locationService.findNearbyDrivers(lon, lat, radius);

        // then
        StepVerifier.create(resultFlux)
                    .assertNext(driver -> {
                        assertThat(driver.driverId()).isEqualTo("101");
                    })
                    .verifyComplete();

        verify(reactiveGeoOperations, times(1)).remove(anyString(), eq("driver:999"));
    }
}