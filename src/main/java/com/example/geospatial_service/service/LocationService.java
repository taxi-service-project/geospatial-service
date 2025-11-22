package com.example.geospatial_service.service;

import com.example.geospatial_service.dto.NearbyDriverResponse;
import com.example.geospatial_service.kafka.dto.DriverLocationUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.geo.Circle;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String DRIVER_LOCATIONS_KEY = "driver_locations";
    private static final String LOCATION_EVENTS_TOPIC = "location_events";

    public Mono<Void> updateDriverLocation(String driverId, double longitude, double latitude) {
        Point point = new Point(longitude, latitude);

        DriverLocationUpdatedEvent event = new DriverLocationUpdatedEvent(driverId, latitude, longitude);
        kafkaTemplate.send(LOCATION_EVENTS_TOPIC, event);

        return reactiveRedisTemplate.opsForGeo()
                                    .add(DRIVER_LOCATIONS_KEY, point, "driver:" + driverId)
                                    .doOnSuccess(result -> log.info("위치 업데이트 성공. Driver ID: {}, Coords: {},{}", driverId, longitude, latitude))
                                    .then();
    }

    public Flux<NearbyDriverResponse> findNearbyDrivers(double longitude, double latitude, int radiusKm) {
        Point center = new Point(longitude, latitude);
        Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);
        Circle circle = new Circle(center, radius);

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                                                                          .includeDistance()
                                                                                          .sortAscending();

        return reactiveRedisTemplate.opsForGeo()
                                    .radius(DRIVER_LOCATIONS_KEY, circle, args)
                                    .map(geoResult -> {
                                        String driverId = geoResult.getContent().getName().split(":")[1];
                                        double distanceKm = geoResult.getDistance().getValue();
                                        return new NearbyDriverResponse(driverId, distanceKm);
                                    })
                                    .doOnSubscribe(s -> log.info("주변 기사 검색 시작. Center: {}, Radius: {}km", center, radiusKm));
    }

}