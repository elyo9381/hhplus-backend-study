package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.AbstractIntegrationTest;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 재시도 메커니즘 테스트
 * 
 * 검증 항목:
 * 1. 재시도 카운트 증가
 * 2. 재시도 3회 초과 시 FAILED 상태로 변경
 * 3. 재시도 3회 미만은 계속 조회됨
 */
class OutboxRetryTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        // 테스트용 Outbox 생성
    }

    @Test
    @DisplayName("재시도 카운트가 3 미만인 Outbox만 조회된다")
    void findByStatusAndRetryCountLessThan_OnlyLessThan3() {
        // Given
        Outbox outbox1 = new Outbox("TEST_EVENT", UUID.randomUUID(), "{}");
        outboxRepository.save(outbox1);

        Outbox outbox2 = new Outbox("TEST_EVENT", UUID.randomUUID(), "{}");
        outbox2.incrementRetry();
        outbox2.incrementRetry();
        outboxRepository.save(outbox2);

        Outbox outbox3 = new Outbox("TEST_EVENT", UUID.randomUUID(), "{}");
        outbox3.incrementRetry();
        outbox3.incrementRetry();
        outbox3.incrementRetry();
        outboxRepository.save(outbox3);

        // When
        List<Outbox> result = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        // Then
        assertThat(result).hasSize(2); // retryCount 0, 2만 조회됨
        assertThat(result).allMatch(o -> o.getRetryCount() < 3);
    }

    @Test
    @DisplayName("재시도 카운트를 증가시킬 수 있다")
    void incrementRetry() {
        // Given
        Outbox outbox = new Outbox("TEST_EVENT", UUID.randomUUID(), "{}");
        outboxRepository.save(outbox);

        // When
        outbox.incrementRetry();
        outboxRepository.save(outbox);

        // Then
        Outbox saved = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3)
                .get(0);
        assertThat(saved.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 3회 초과 시 FAILED 상태로 변경할 수 있다")
    void markAsFailed_After3Retries() {
        // Given
        Outbox outbox = new Outbox("TEST_EVENT", UUID.randomUUID(), "{}");
        outbox.incrementRetry();
        outbox.incrementRetry();
        outbox.incrementRetry();
        outbox.markAsFailed();
        outboxRepository.save(outbox);

        // When
        List<Outbox> pending = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        // Then
        assertThat(pending).isEmpty(); // FAILED 상태는 조회 안 됨
    }

    @Test
    @DisplayName("에러 메시지를 저장할 수 있다")
    void setErrorMessage() {
        // Given
        Outbox outbox = new Outbox("TEST_EVENT", UUID.randomUUID(), "{}");
        String errorMessage = "Connection timeout";

        // When
        outbox.setErrorMessage(errorMessage);
        outboxRepository.save(outbox);

        // Then
        Outbox saved = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3)
                .get(0);
        assertThat(saved.getErrorMessage()).isEqualTo(errorMessage);
    }
}
