package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 도메인 단위 테스트
 * 
 * 순수 Java 테스트 - DB 불필요
 */
class OutboxDomainTest {

    @Test
    @DisplayName("Outbox 생성 시 PENDING 상태이다")
    void createOutbox_StatusIsPending() {
        Outbox outbox = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("markAsPublished 호출 시 PUBLISHED 상태로 변경된다")
    void markAsPublished() {
        Outbox outbox = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");

        outbox.markAsPublished();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outbox.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("incrementRetry 호출 시 retryCount가 증가한다")
    void incrementRetry() {
        Outbox outbox = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");

        outbox.incrementRetry();
        assertThat(outbox.getRetryCount()).isEqualTo(1);

        outbox.incrementRetry();
        assertThat(outbox.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("markAsFailed 호출 시 FAILED 상태로 변경된다")
    void markAsFailed() {
        Outbox outbox = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");

        outbox.markAsFailed();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("에러 메시지를 설정할 수 있다")
    void setErrorMessage() {
        Outbox outbox = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");

        outbox.setErrorMessage("Connection timeout");

        assertThat(outbox.getErrorMessage()).isEqualTo("Connection timeout");
    }
}
