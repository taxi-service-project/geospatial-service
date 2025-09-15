package com.example.geospatial_service.service;

import com.example.geospatial_service.dto.NearbyDriverResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class LocationServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private LocationService locationService;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private static final String DRIVER_LOCATIONS_KEY = "driver_locations";

    @AfterEach
    void tearDown() {
        // 각 테스트 후 Redis 데이터 정리
        reactiveRedisTemplate.delete(DRIVER_LOCATIONS_KEY).block();
    }

    @Test
    @DisplayName("실제 Redis 환경에서 주변 기사 검색이 성공적으로 동작해야 한다")
    void findNearbyDrivers_IntegrationTest_Success() {
        // given
        // 여러 기사 위치 정보 추가
        locationService.updateDriverLocation("driver1", 127.001, 37.501).block(); // 약 0.15km
        locationService.updateDriverLocation("driver2", 127.005, 37.505).block(); // 약 0.78km
        locationService.updateDriverLocation("driver3", 127.020, 37.520).block(); // 약 2.9km
        locationService.updateDriverLocation("driver4", 127.050, 37.550).block(); // 약 7.8km (검색 반경 밖)

        // when
        // 중심점(127.0, 37.5)에서 반경 5km 내 기사 검색
        var nearbyDrivers = locationService.findNearbyDrivers(127.0, 37.5, 5);

        // then
        StepVerifier.create(nearbyDrivers.collectList())
                .assertNext(drivers -> {
                    assertThat(drivers).hasSize(3);
                    assertThat(drivers).map(NearbyDriverResponse::driverId)
                            .containsExactly("driver1", "driver2", "driver3"); // 거리순 정렬 확인

                    // 거리 값 검증 (근사치)
                    assertThat(drivers.get(0).distanceKm()).isBetween(0.1, 0.2);
                    assertThat(drivers.get(1).distanceKm()).isBetween(0.7, 0.8);
                    assertThat(drivers.get(2).distanceKm()).isBetween(2.8, 3.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("주변에 기사가 없을 때 빈 Flux를 반환해야 한다")
    void findNearbyDrivers_IntegrationTest_Empty() {
        // given
        // 기사 위치 정보 없음

        // when
        var nearbyDrivers = locationService.findNearbyDrivers(127.0, 37.5, 5);

        // then
        StepVerifier.create(nearbyDrivers)
                .expectNextCount(0)
                .verifyComplete();
    }
}
