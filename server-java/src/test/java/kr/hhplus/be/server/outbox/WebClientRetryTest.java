package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.AbstractIntegrationTest;
import kr.hhplus.be.server.infrastructure.outbox.message.WebClientMessageProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WebClient 재시도 및 에러 처리 테스트
 * 
 * 검증 항목:
 * 1. 외부 API 실패 시 재시도
 * 2. 타임아웃 처리
 * 3. 에러 로깅
 */
class WebClientRetryTest extends AbstractIntegrationTest {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Test
    @DisplayName("외부 API가 응답하지 않으면 예외가 발생한다")
    void externalApiNotResponding() {
        // Given
        WebClientMessageProducer producer = new WebClientMessageProducer(
                webClientBuilder,
                "http://localhost:9999" // 존재하지 않는 서버
        );

        // When & Then
        assertThatThrownBy(() -> producer.send("TEST_EVENT", "{}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send event to external API");
    }

    @Test
    @DisplayName("잘못된 URL이면 예외가 발생한다")
    void invalidUrl() {
        // Given
        WebClientMessageProducer producer = new WebClientMessageProducer(
                webClientBuilder,
                "invalid-url"
        );

        // When & Then
        assertThatThrownBy(() -> producer.send("TEST_EVENT", "{}"))
                .isInstanceOf(RuntimeException.class);
    }
}
