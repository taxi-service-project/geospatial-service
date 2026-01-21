package com.example.geospatial_service.kafka;

import com.example.geospatial_service.kafka.dto.TripCanceledEvent;
import com.example.geospatial_service.kafka.dto.TripCompletedEvent;
import com.example.geospatial_service.kafka.dto.TripMatchedEvent;
import com.example.geospatial_service.kafka.dto.socket.DriverConfigMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
@KafkaListener(topics = "trip_events", groupId = "geospatial-service-group")
public class DriverConfigConsumer {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaHandler
    public void handleTripMatched(TripMatchedEvent event) {
        publishConfig(event.driverId(), DriverConfigMessage.highFrequency());
    }

    @KafkaHandler
    public void handleTripCompleted(TripCompletedEvent event) {
        publishConfig(event.driverId(), DriverConfigMessage.lowFrequency());
    }

    @KafkaHandler
    public void handleTripCanceled(TripCanceledEvent event) {
        publishConfig(event.driverId(), DriverConfigMessage.lowFrequency());
    }

    private void publishConfig(String driverId, DriverConfigMessage message) {
        String topic = "driver:config:" + driverId;

        Mono.fromCallable(() -> objectMapper.writeValueAsString(message))
            .flatMap(json -> reactiveRedisTemplate.convertAndSend(topic, json))
            .doOnSuccess(count -> {
                if (count == 0) log.warn("설정 메시지를 받은 서버가 없습니다 (기사 오프라인 가능성): {}", driverId);
                else log.info("기사({}) 설정 방송 완료", driverId);
            })
            .doOnError(e -> log.error("Redis 방송 실패: {}", driverId, e))
            .subscribe();
    }
}