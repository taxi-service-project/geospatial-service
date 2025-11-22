package com.example.geospatial_service.kafka;

import com.example.geospatial_service.handler.LocationWebSocketHandler;
import com.example.geospatial_service.kafka.dto.TripCanceledEvent;
import com.example.geospatial_service.kafka.dto.TripCompletedEvent;
import com.example.geospatial_service.kafka.dto.TripMatchedEvent;
import com.example.geospatial_service.kafka.dto.socket.DriverConfigMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@KafkaListener(topics = "trip_events", groupId = "geospatial-service-group")
public class DriverConfigConsumer {

    private final LocationWebSocketHandler locationWebSocketHandler;

    // 배차 완료 -> 바빠짐 -> 1초 주기
    @KafkaHandler
    public void handleTripMatched(TripMatchedEvent event) {
        locationWebSocketHandler.sendConfigToDriver(event.driverId(), DriverConfigMessage.highFrequency());
    }

    // 운행 종료 -> 한가해짐 -> 10초 주기
    @KafkaHandler
    public void handleTripCompleted(TripCompletedEvent event) {
        locationWebSocketHandler.sendConfigToDriver(event.driverId(), DriverConfigMessage.lowFrequency());
    }

    @KafkaHandler
    public void handleTripCanceled(TripCanceledEvent event) {
        log.info("여정 취소 수신 -> 기사({}) 10초 주기로 변경", event.driverId());
        locationWebSocketHandler.sendConfigToDriver(event.driverId(), DriverConfigMessage.lowFrequency());
    }

    @KafkaHandler(isDefault = true)
    public void handleUnknown(Object event) {
        log.warn("알 수 없는 이벤트 수신: {}", event);
    }
}