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
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;

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

        // Input: 기사가 보내는 위치 정보 처리
        Mono<Void> input = session.receive()
                                  // 타임아웃을 맨 위로 (메시지가 들어오면 무조건 타이머 리셋)
                                  .timeout(Duration.ofSeconds(30))
                                  // [Filter] PONG은 타임아웃 리셋용이므로, 비즈니스 로직으로 넘기지 않고 여기서 소멸시킴
                                  .filter(msg -> {
                                      String payload = msg.getPayloadAsText();
                                      // PONG이면 로그만 찍고 버림 (false 반환)
                                      if ("PONG".equalsIgnoreCase(payload)) {
                                          log.trace("기사({}) 생존 확인 (PONG)", driverId);
                                          return false;
                                      }
                                      return true; // 위치 정보면 통과
                                  })
                                  .sample(Duration.ofMillis(1000))
                                  .flatMap(msg -> {
                                      String payload = msg.getPayloadAsText();

                                      // 실제 위치 정보 처리
                                      return Mono.fromCallable(() -> objectMapper.readValue(payload, UpdateLocationRequest.class))
                                                 .flatMap(req -> locationService.updateDriverLocation(driverId, req.longitude(), req.latitude()))
                                                 .doOnError(e -> log.error("위치 업데이트 실패: {}", driverId, e))
                                                 .onErrorResume(e -> Mono.empty());
                                  })
                                  .onErrorResume(e -> {
                                      // Timeout 발생 시 로그 찍고 종료
                                      if (e instanceof java.util.concurrent.TimeoutException) {
                                          log.warn("기사 연결 타임아웃 종료: {}", driverId);
                                      }
                                      return Mono.empty();
                                  })
                                  .then();

        Flux<WebSocketMessage> configFlux = reactiveRedisTemplate.listenTo(ChannelTopic.of(configTopic))
                                                                .map(message -> session.textMessage(message.getMessage()))
                                                                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)) // 최대 3번 재시도, 시작은 2초부터 지수적으로 증가
                                                                                .doBeforeRetry(retrySignal ->
                                                                                        log.warn("Redis 구독 재시도 중... (시도 횟수: {})", retrySignal.totalRetries() + 1)
                                                                                )
                                                                                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                                                                                    log.error("Redis 구독 최종 실패: 재시도 횟수 초과");
                                                                                    return retrySignal.failure();
                                                                                })
                                                                )
                                                                .doOnError(e -> log.error("Redis Pub/Sub Fatal Error: {}", e.getMessage()));

        // 10초마다 PING 전송
        Flux<WebSocketMessage> pingFlux = Flux.interval(Duration.ofSeconds(10))
                                              .map(i -> session.textMessage("PING"));

        // Output: Config + Ping 병합 전송
        Mono<Void> output = session.send(Flux.merge(configFlux, pingFlux));

        return Mono.zip(input, output).then()
                   .doFinally(signal -> log.info("기사 연결 종료: {}", driverId));
    }

    private String extractDriverId(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}