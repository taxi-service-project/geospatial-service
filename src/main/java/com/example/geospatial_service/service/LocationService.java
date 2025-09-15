package com.example.geospatial_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final String DRIVER_LOCATIONS_KEY = "driver_locations";

    public Mono<Void> updateDriverLocation(String driverId, double longitude, double latitude) {
        Point point = new Point(longitude, latitude);

        return reactiveRedisTemplate.opsForGeo()
                                    .add(DRIVER_LOCATIONS_KEY, point, "driver:" + driverId)
                                    .doOnSuccess(result -> log.info("위치 업데이트 성공. Driver ID: {}, Coords: {},{}", driverId, longitude, latitude))
                                    .then();
    }
}