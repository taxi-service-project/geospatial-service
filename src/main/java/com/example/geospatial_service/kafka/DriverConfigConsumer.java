package com.example.geospatial_service.kafka;

import com.example.geospatial_service.handler.LocationWebSocketHandler;
import com.example.geospatial_service.kafka.dto.TripCompletedEvent;
import com.example.geospatial_service.kafka.dto.TripMatchedEvent;
import com.example.geospatial_service.kafka.dto.socket.DriverConfigMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DriverConfigConsumer {

    private final LocationWebSocketHandler locationWebSocketHandler;

    // 배차 완료 -> 바빠짐 -> 1초 주기
    @KafkaListener(topics = "trip_events", groupId = "geospatial-group-matched")
    public void handleTripMatched(TripMatchedEvent event) {
        locationWebSocketHandler.sendConfigToDriver(event.driverId(), DriverConfigMessage.highFrequency());
    }

    // 운행 종료 -> 한가해짐 -> 10초 주기
    @KafkaListener(topics = "trip_events", groupId = "geospatial-group-completed")
    public void handleTripCompleted(TripCompletedEvent event) {
        locationWebSocketHandler.sendConfigToDriver(event.driverId(), DriverConfigMessage.lowFrequency());
    }
}