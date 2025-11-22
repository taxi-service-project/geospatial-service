package com.example.geospatial_service.handler;

import com.example.geospatial_service.dto.UpdateLocationRequest;
import com.example.geospatial_service.kafka.dto.socket.DriverConfigMessage;
import com.example.geospatial_service.service.LocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocationWebSocketHandler extends TextWebSocketHandler {

    private final LocationService locationService;
    private final ObjectMapper objectMapper;

    private final Map<String, WebSocketSession> driverSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String driverId = extractDriverId(session);
        driverSessions.put(driverId, session);

        log.info("기사 연결됨. Driver ID: {}, Session ID: {}", driverId, session.getId());
        // 연결 초기에는 대기 모드(10초)로 설정
        sendConfigToDriver(driverId, DriverConfigMessage.lowFrequency());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String driverId = extractDriverId(session);
        try {
            UpdateLocationRequest request = objectMapper.readValue(message.getPayload(), UpdateLocationRequest.class);

            locationService.updateDriverLocation(driverId, request.longitude(), request.latitude())
                           .subscribe(null, e -> log.error("위치 업데이트 실패: {}", driverId, e));

        } catch (IOException e) {
            log.error("메시지 처리 실패: {}", driverId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String driverId = extractDriverId(session);
        driverSessions.remove(driverId);
        log.info("기사 연결 끊김. Driver ID: {}", driverId);
    }

    public void sendConfigToDriver(String driverId, DriverConfigMessage message) {
        WebSocketSession session = driverSessions.get(driverId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
                log.info("기사({}) 주기 변경 전송: {}ms", driverId, message.payload().locationIntervalMs());
            } catch (IOException e) {
                log.error("설정 전송 실패: {}", driverId, e);
            }
        }
    }

    private String extractDriverId(WebSocketSession session) {
        String path = Objects.requireNonNull(session.getUri()).getPath();
        String prefix = "/ws/location/";
        if (path.startsWith(prefix)) {
            String id = path.substring(prefix.length());
            if (!id.isBlank()) return id;
        }
        throw new IllegalArgumentException("Invalid Driver URL: " + path);
    }
}