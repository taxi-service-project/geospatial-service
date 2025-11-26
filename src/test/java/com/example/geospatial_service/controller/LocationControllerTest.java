package com.example.geospatial_service.controller;

import com.example.geospatial_service.dto.NearbyDriverResponse;
import com.example.geospatial_service.service.LocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

// WebFlux 컨트롤러 전용 슬라이스 테스트
@WebFluxTest(controllers = LocationController.class)
class LocationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private LocationService locationService;

    @Test
    @DisplayName("정상 파라미터로 주변 기사 조회 시 200 OK와 데이터 스트림을 반환한다")
    void searchNearbyDrivers_Success() {
        // Given
        double longitude = 127.1234;
        double latitude = 37.5678;
        int radius = 5;

        NearbyDriverResponse driver1 = new NearbyDriverResponse("DriverA", 4.5);
        NearbyDriverResponse driver2 = new NearbyDriverResponse("DriverB", 4.8);

        given(locationService.findNearbyDrivers(anyDouble(), anyDouble(), anyInt()))
                .willReturn(Flux.just(driver1, driver2));

        // When & Then
        webTestClient.get()
                     .uri(uriBuilder -> uriBuilder
                             .path("/api/locations/search")
                             .queryParam("longitude", longitude)
                             .queryParam("latitude", latitude)
                             .queryParam("radius", radius)
                             .build())
                     .accept(MediaType.APPLICATION_JSON)
                     .exchange()
                     .expectStatus().isOk()
                     .expectBodyList(NearbyDriverResponse.class)
                     .hasSize(2)
                     .contains(driver1, driver2);
    }

    @Test
    @DisplayName("경도(longitude) 범위를 벗어나면 400 Bad Request를 반환한다")
    void searchNearbyDrivers_InvalidLongitude() {
        // Given
        double invalidLongitude = 200.0; // Max 180 초과

        // When & Then
        webTestClient.get()
                     .uri(uriBuilder -> uriBuilder
                             .path("/api/locations/search")
                             .queryParam("longitude", invalidLongitude) // 잘못된 값
                             .queryParam("latitude", 37.0)
                             .queryParam("radius", 5)
                             .build())
                     .exchange()
                     .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("반경(radius)이 음수이면 400 Bad Request를 반환한다")
    void searchNearbyDrivers_InvalidRadius() {
        // Given
        int invalidRadius = -1; // Min 1 미만

        // When & Then
        webTestClient.get()
                     .uri(uriBuilder -> uriBuilder
                             .path("/api/locations/search")
                             .queryParam("longitude", 127.0)
                             .queryParam("latitude", 37.0)
                             .queryParam("radius", invalidRadius)
                             .build())
                     .exchange()
                     .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("결과가 없는 경우 빈 리스트(Empty)를 반환하고 200 OK여야 한다")
    void searchNearbyDrivers_EmptyResult() {
        // Given
        given(locationService.findNearbyDrivers(anyDouble(), anyDouble(), anyInt()))
                .willReturn(Flux.empty());

        // When & Then
        webTestClient.get()
                     .uri(uriBuilder -> uriBuilder
                             .path("/api/locations/search")
                             .queryParam("longitude", 127.0)
                             .queryParam("latitude", 37.0)
                             .queryParam("radius", 5)
                             .build())
                     .exchange()
                     .expectStatus().isOk()
                     .expectBodyList(NearbyDriverResponse.class)
                     .hasSize(0);
    }
}