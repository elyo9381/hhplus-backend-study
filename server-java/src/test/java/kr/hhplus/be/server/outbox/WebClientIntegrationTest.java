package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.AbstractIntegrationTest;
import kr.hhplus.be.server.application.outbox.MessageProducer;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.infrastructure.outbox.scheduler.OutboxScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebClient 외부 API 연동 통합 테스트
 * 
 * 검증 항목:
 * 1. WebClient로 외부 API 호출
 * 2. Mock 외부 API 서버에서 이벤트 수신
 * 3. Outbox → WebClient → 외부 API 전체 플로우
 */
@TestPropertySource(properties = {
        "external.api.url=http://localhost:${local.server.port}"
})
class WebClientIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxScheduler outboxScheduler;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Mock 외부 API 이벤트 초기화
        restTemplate.delete("http://localhost:" + port + "/api/events");
    }

    @Test
    @DisplayName("WebClient로 외부 API에 이벤트를 전송할 수 있다")
    void sendEventToExternalApi() {
        // Given
        String eventType = "TEST_EVENT";
        String payload = "{\"test\": \"data\"}";

        // When
        messageProducer.send(eventType, payload);

        // Then
        // Mock 외부 API에서 이벤트 수신 확인
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/events",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("eventType")).isEqualTo(eventType);
    }

    @Test
    @DisplayName("Outbox 스케줄러가 WebClient를 통해 외부 API에 이벤트를 발행한다")
    void outboxSchedulerSendsToExternalApi() {
        // Given
        Outbox outbox = new Outbox(
                "ORDER_CREATED",
                UUID.randomUUID(),
                "{\"orderId\": \"123\", \"amount\": 10000}"
        );
        outboxRepository.save(outbox);

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        // 1. Outbox 상태가 PUBLISHED로 변경됨
        List<Outbox> published = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PUBLISHED, 3);
        assertThat(published).hasSize(1);

        // 2. Mock 외부 API에서 이벤트 수신 확인
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/events",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("eventType")).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("여러 Outbox 이벤트를 순차적으로 외부 API에 발행한다")
    void sendMultipleEvents() {
        // Given
        Outbox outbox1 = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");
        Outbox outbox2 = new Outbox("PAYMENT_COMPLETED", UUID.randomUUID(), "{}");
        outboxRepository.save(outbox1);
        outboxRepository.save(outbox2);

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/events",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody())
                .extracting(event -> event.get("eventType"))
                .containsExactlyInAnyOrder("ORDER_CREATED", "PAYMENT_COMPLETED");
    }
}
