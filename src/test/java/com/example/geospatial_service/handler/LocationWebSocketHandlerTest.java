package com.example.geospatial_service.handler;

import com.example.geospatial_service.dto.UpdateLocationRequest;
import com.example.geospatial_service.service.LocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationWebSocketHandlerTest {

    @InjectMocks
    private LocationWebSocketHandler webSocketHandler;

    @Mock
    private LocationService locationService;

    @Spy
    private ObjectMapper objectMapper;

    @Mock
    private WebSocketSession session;

    @Test
    @DisplayName("웹소켓으로 위치 정보 메시지를 받으면 서비스 로직을 호출해야 한다")
    void handleTextMessage_Success() throws Exception {
        // given
        String driverId = "101";
        UpdateLocationRequest request = new UpdateLocationRequest(127.5, 37.5);
        String jsonPayload = objectMapper.writeValueAsString(request);
        TextMessage message = new TextMessage(jsonPayload);

        when(session.getUri()).thenReturn(new URI("/ws/location/" + driverId));
        when(locationService.updateDriverLocation(eq(driverId), anyDouble(), anyDouble()))
                .thenReturn(Mono.empty());

        // when
        webSocketHandler.handleTextMessage(session, message);

        // then
        verify(locationService).updateDriverLocation(driverId, 127.5, 37.5);
    }
}