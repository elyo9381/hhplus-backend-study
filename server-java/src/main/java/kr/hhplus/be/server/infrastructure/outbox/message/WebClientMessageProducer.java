package kr.hhplus.be.server.infrastructure.outbox.message;

import kr.hhplus.be.server.application.outbox.MessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

/**
 * WebClient 기반 외부 API 메시지 발행
 * 
 * 특징:
 * - 비동기 논블로킹 I/O
 * - 자동 재시도 (3회, Exponential Backoff)
 * - 타임아웃 설정 (10초)
 * - 에러 로깅
 */
@Slf4j
@Component
public class WebClientMessageProducer implements MessageProducer {

    private final WebClient webClient;
    private final String externalApiUrl;

    public WebClientMessageProducer(
            WebClient.Builder webClientBuilder,
            @Value("${external.api.url:http://localhost:9090}") String externalApiUrl) {
        this.externalApiUrl = externalApiUrl;
        this.webClient = webClientBuilder
                .baseUrl(externalApiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        log.info("WebClientMessageProducer initialized with URL: {}", externalApiUrl);
    }

    @Override
    public void send(String eventType, String payload) {
        log.info("Sending event to external API: eventType={}", eventType);
        
        try {
            webClient.post()
                    .uri("/api/events")
                    .bodyValue(Map.of(
                            "eventType", eventType,
                            "payload", payload,
                            "timestamp", System.currentTimeMillis()
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .maxBackoff(Duration.ofSeconds(5))
                            .doBeforeRetry(retrySignal -> 
                                    log.warn("Retrying... attempt: {}", retrySignal.totalRetries() + 1)))
                    .doOnSuccess(response -> 
                            log.info("Successfully sent event: eventType={}, response={}", eventType, response))
                    .doOnError(error -> 
                            log.error("Failed to send event: eventType={}, error={}", eventType, error.getMessage()))
                    .onErrorResume(WebClientResponseException.class, e -> {
                        log.error("HTTP error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                        return Mono.error(e);
                    })
                    .block(Duration.ofSeconds(10));
                    
        } catch (Exception e) {
            log.error("Exception while sending event: eventType={}", eventType, e);
            throw new RuntimeException("Failed to send event to external API", e);
        }
    }
}
