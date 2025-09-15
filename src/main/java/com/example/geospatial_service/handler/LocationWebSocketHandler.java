package com.example.geospatial_service.handler;

import com.example.geospatial_service.dto.UpdateLocationRequest;
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
import java.net.URI;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocationWebSocketHandler extends TextWebSocketHandler {

    private final LocationService locationService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String driverId = extractDriverId(session);
        log.info("기사 연결됨. Driver ID: {}, Session ID: {}", driverId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String driverId = extractDriverId(session);
        try {
            UpdateLocationRequest request = objectMapper.readValue(message.getPayload(), UpdateLocationRequest.class);
            locationService.updateDriverLocation(driverId, request.longitude(), request.latitude())
                           .subscribe(
                                   null,
                                   error -> log.error("위치 업데이트 중 Redis 오류 발생. Driver ID: {}", driverId,
                                           error)
                           );

        } catch (IOException e) {
            log.error("위치 정보 메시지 처리 실패. Driver ID: {}, Payload: {}", driverId, message.getPayload(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String driverId = extractDriverId(session);
        log.info("기사 연결 끊김. Driver ID: {}, Status: {}", driverId, status);
    }

    private String extractDriverId(WebSocketSession session) {
        String path = Objects.requireNonNull(session.getUri()).getPath();
        String prefix = "/ws/location/";
        if (path.startsWith(prefix)) {
            String driverId = path.substring(prefix.length());
            if (!driverId.isBlank()) {
                return driverId;
            }
        }
        throw new IllegalArgumentException("URL에서 유효한 Driver ID를 추출할 수 없습니다: " + path);
    }
}