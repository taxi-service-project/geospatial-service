package com.example.geospatial_service.service;

import com.example.geospatial_service.dto.NearbyDriverResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    // @InjectMocks 대신 직접 생성할 것이므로 선언만 합니다.
    private LocationService locationService;

    @Mock
    private ReactiveRedisTemplate<String, String> cacheRedisTemplate;

    @Mock
    private ReactiveRedisTemplate<String, String> storageRedisTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ReactiveGeoOperations<String, String> reactiveGeoOperations;

    @BeforeEach
    void setUp() {
        // 같은 타입의 Mock이 2개일 때는 직접 생성자로 주입하는 것이 가장 안전
        locationService = new LocationService(cacheRedisTemplate, storageRedisTemplate, kafkaTemplate);
    }

    @Test
    @DisplayName("주변 기사 검색 시, '생존 신고(Active Key)'가 있는 기사만 반환해야 한다")
    void findNearbyDrivers_Success_OnlyActiveDrivers() {
        // given
        double lon = 127.0, lat = 37.5;
        int radius = 5;

        // Redis GeoResult Mock 데이터
        var result1 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("driver:101", new Point(127.01, 37.51)),
                new Distance(1.5, Metrics.KILOMETERS)
        );
        var result2 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("driver:102", new Point(127.02, 37.52)),
                new Distance(2.5, Metrics.KILOMETERS)
        );

        given(cacheRedisTemplate.opsForGeo()).willReturn(reactiveGeoOperations);
        given(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .willReturn(Flux.just(result1, result2));

        given(storageRedisTemplate.hasKey("driver_active:101")).willReturn(Mono.just(true));
        given(storageRedisTemplate.hasKey("driver_active:102")).willReturn(Mono.just(true));

        // when
        Flux<NearbyDriverResponse> resultFlux = locationService.findNearbyDrivers(lon, lat, radius);

        // then
        StepVerifier.create(resultFlux)
                    .assertNext(driver -> assertThat(driver.driverId()).isEqualTo("101"))
                    .assertNext(driver -> assertThat(driver.driverId()).isEqualTo("102"))
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

        given(cacheRedisTemplate.opsForGeo()).willReturn(reactiveGeoOperations);
        given(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .willReturn(Flux.just(activeDriver, zombieDriver));

        given(storageRedisTemplate.hasKey("driver_active:101")).willReturn(Mono.just(true));
        given(storageRedisTemplate.hasKey("driver_active:999")).willReturn(Mono.just(false));

        given(cacheRedisTemplate.opsForGeo().remove(anyString(), eq("driver:999")))
                .willReturn(Mono.just(1L));

        // when
        Flux<NearbyDriverResponse> resultFlux = locationService.findNearbyDrivers(lon, lat, radius);

        // then
        StepVerifier.create(resultFlux)
                    .assertNext(driver -> assertThat(driver.driverId()).isEqualTo("101"))
                    .verifyComplete();

        // Verify: 실제로 삭제 메서드가 호출되었는지 검증
        verify(reactiveGeoOperations).remove(anyString(), eq("driver:999"));
        verify(reactiveGeoOperations, never()).remove(anyString(), eq("driver:101"));
    }
}