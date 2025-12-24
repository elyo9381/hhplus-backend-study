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
 * Outbox 기본 기능 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxBasicTest extends TestContainerSupport {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
    }

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxScheduler outboxScheduler;

    @Autowired
    private MockMessageProducer mockMessageProducer;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        mockMessageProducer.clear();
    }

    @Test
    @DisplayName("Outbox를 저장할 수 있다")
    void saveOutbox() {
        // Given
        Outbox outbox = new Outbox(
                "TEST_EVENT",
                UUID.randomUUID(),
                "{\"test\": \"data\"}"
        );

        // When
        Outbox saved = outboxRepository.save(outbox);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventType()).isEqualTo("TEST_EVENT");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING 상태의 Outbox를 조회할 수 있다")
    void findPendingOutbox() {
        // Given
        Outbox outbox = new Outbox(
                "TEST_EVENT",
                UUID.randomUUID(),
                "{\"test\": \"data\"}"
        );
        outboxRepository.save(outbox);

        // When
        List<Outbox> pending = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        // Then
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEventType()).isEqualTo("TEST_EVENT");
    }

    @Test
    @DisplayName("OutboxScheduler가 이벤트를 발행한다")
    void publishEvent() {
        // Given
        Outbox outbox = new Outbox(
                "TEST_EVENT",
                UUID.randomUUID(),
                "{\"test\": \"data\"}"
        );
        outboxRepository.save(outbox);

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        List<MockMessageProducer.Message> messages = mockMessageProducer.getSentMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getEventType()).isEqualTo("TEST_EVENT");
    }

    @Test
    @DisplayName("발행 후 Outbox 상태가 PUBLISHED로 변경된다")
    void publishedStatus() {
        // Given
        Outbox outbox = new Outbox(
                "TEST_EVENT",
                UUID.randomUUID(),
                "{\"test\": \"data\"}"
        );
        outboxRepository.save(outbox);

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        List<Outbox> pending = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);
        assertThat(pending).isEmpty();

        List<Outbox> published = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PUBLISHED, 3);
        assertThat(published).hasSize(1);
        assertThat(published.get(0).getPublishedAt()).isNotNull();
    }
}
