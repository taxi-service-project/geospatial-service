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

import java.time.Duration;

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

        try {
            Boolean success = Mono.fromCallable(() -> objectMapper.writeValueAsString(message))
                                  .flatMap(json -> reactiveRedisTemplate.convertAndSend(topic, json))
                                  .map(count -> count > 0)
                                  .block(Duration.ofSeconds(2));

            if (Boolean.TRUE.equals(success)) {
                log.info("기사({}) 설정 방송 성공", driverId);
            } else {
                log.warn("기사 오프라인 (수신 서버 없음): {}", driverId);
            }

        } catch (Exception e) {
            log.error("Redis 방송 실패 (재시도 대상): {}", driverId);
            throw new RuntimeException("Redis 전송 장애로 인한 재시도 유도", e);
        }
    }
}