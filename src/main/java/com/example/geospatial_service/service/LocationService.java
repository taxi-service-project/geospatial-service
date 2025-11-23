package com.example.geospatial_service.service;

import com.example.geospatial_service.dto.NearbyDriverResponse;
import com.example.geospatial_service.kafka.dto.DriverLocationUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class LocationService {

    private final ReactiveRedisTemplate<String, String> cacheRedisTemplate;

    private final ReactiveRedisTemplate<String, String> storageRedisTemplate;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String LOCATION_EVENTS_TOPIC = "location_events";
    private static final String DRIVER_LOCATIONS_KEY = "driver_locations";
    private static final String DRIVER_ACTIVE_KEY_PREFIX = "driver_active:";

    public LocationService(
            @Qualifier("cacheRedisTemplate") ReactiveRedisTemplate<String, String> cacheRedisTemplate,
            @Qualifier("storageRedisTemplate") ReactiveRedisTemplate<String, String> storageRedisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.cacheRedisTemplate = cacheRedisTemplate;
        this.storageRedisTemplate = storageRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Mono<Void> updateDriverLocation(String driverId, double longitude, double latitude) {
        Point point = new Point(longitude, latitude);

        kafkaTemplate.send(LOCATION_EVENTS_TOPIC, new DriverLocationUpdatedEvent(driverId, latitude, longitude));

        Mono<Long> geoAdd = cacheRedisTemplate.opsForGeo()
                                              .add(DRIVER_LOCATIONS_KEY, point, "driver:" + driverId);

        // [Storage] 생존 신고 (30초 TTL)
        // 기사가 앱을 끄면 이 키가 갱신되지 않아 30초 뒤 사라짐 -> 오프라인 처리됨
        Mono<Boolean> setAlive = storageRedisTemplate.opsForValue()
                                                     .set(DRIVER_ACTIVE_KEY_PREFIX + driverId, "1", Duration.ofSeconds(30));

        return Mono.zip(geoAdd, setAlive)
                   .doOnSuccess(t -> log.debug("위치 업데이트 완료: {}", driverId))
                   .then();
    }

    public Flux<NearbyDriverResponse> findNearbyDrivers(double longitude, double latitude, int radiusKm) {
        Point center = new Point(longitude, latitude);
        Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);
        Circle circle = new Circle(center, radius);

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                                                                          .includeDistance()
                                                                                          .sortAscending()
                                                                                          .limit(50);

        return cacheRedisTemplate.opsForGeo()
                                 .radius(DRIVER_LOCATIONS_KEY, circle, args)
                                 .flatMap(geoResult -> {
                                     String memberName = geoResult.getContent().getName();
                                     String driverId = memberName.split(":")[1];
                                     double distance = geoResult.getDistance().getValue();

                                     return storageRedisTemplate.hasKey(DRIVER_ACTIVE_KEY_PREFIX + driverId)
                                                                .flatMap(isAlive -> {
                                                                    if (isAlive) {
                                                                        // ✅ 살아있음 -> 결과 반환
                                                                        return Mono.just(new NearbyDriverResponse(driverId, distance));
                                                                    } else {
                                                                        // ❌ 죽었음(TTL 만료) -> [Cache]에서 위치 데이터 삭제
                                                                        return cacheRedisTemplate.opsForGeo()
                                                                                                 .remove(DRIVER_LOCATIONS_KEY, memberName)
                                                                                                 .then(Mono.empty());
                                                                    }
                                                                });
                                 })
                                 .doOnSubscribe(s -> log.info("주변 기사 검색 시작. Center: {}, Radius: {}km", center, radiusKm));
    }
}