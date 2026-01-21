package com.example.geospatial_service.handler;

import com.example.geospatial_service.dto.UpdateLocationRequest;
import com.example.geospatial_service.service.LocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReactiveLocationHandler implements WebSocketHandler {

    private final LocationService locationService;
    private final ObjectMapper objectMapper;

    @Qualifier("cacheRedisTemplate")
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String driverId = extractDriverId(session);
        String configTopic = "driver:config:" + driverId;

        Mono<Void> input = session.receive()
                                  .flatMap(msg ->
                                          Mono.fromCallable(() -> objectMapper.readValue(msg.getPayloadAsText(), UpdateLocationRequest.class))
                                              .flatMap(req -> locationService.updateDriverLocation(driverId, req.longitude(), req.latitude()))
                                              .doOnError(e -> log.error("위치 업데이트 실패 (세션 유지): {}", driverId, e))
                                              .onErrorResume(e -> Mono.empty())
                                  )
                                  .then();

        Mono<Void> output = session.send(
                reactiveRedisTemplate.listenTo(ChannelTopic.of(configTopic))
                                     .map(message -> session.textMessage(message.getMessage()))
        );

        return Mono.zip(input, output).then()
                   .doFinally(signal -> log.info("기사 연결 종료: {}", driverId));
    }

    private String extractDriverId(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}