package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.TestContainerSupport;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.infrastructure.outbox.message.MockMessageProducer;
import kr.hhplus.be.server.presentation.scheduler.OutboxScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 메시지 발행 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class WebClientIntegrationTest extends TestContainerSupport {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
    }

    @Autowired
    private MockMessageProducer mockMessageProducer;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxScheduler outboxScheduler;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        mockMessageProducer.clear();
    }

    @Test
    @DisplayName("MessageProducer로 이벤트를 전송할 수 있다")
    void sendEventToExternalApi() {
        // Given
        String eventType = "TEST_EVENT";
        String payload = "{\"test\": \"data\"}";

        // When
        mockMessageProducer.send(eventType, payload);

        // Then
        List<MockMessageProducer.Message> messages = mockMessageProducer.getSentMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getEventType()).isEqualTo(eventType);
    }

    @Test
    @DisplayName("Outbox 스케줄러가 이벤트를 발행한다")
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
        List<Outbox> published = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PUBLISHED, 3);
        assertThat(published).hasSize(1);

        List<MockMessageProducer.Message> messages = mockMessageProducer.getSentMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getEventType()).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("여러 Outbox 이벤트를 순차적으로 발행한다")
    void sendMultipleEvents() {
        // Given
        Outbox outbox1 = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");
        Outbox outbox2 = new Outbox("PAYMENT_COMPLETED", UUID.randomUUID(), "{}");
        outboxRepository.save(outbox1);
        outboxRepository.save(outbox2);

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        List<MockMessageProducer.Message> messages = mockMessageProducer.getSentMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages)
                .extracting(MockMessageProducer.Message::getEventType)
                .containsExactlyInAnyOrder("ORDER_CREATED", "PAYMENT_COMPLETED");
    }
}
