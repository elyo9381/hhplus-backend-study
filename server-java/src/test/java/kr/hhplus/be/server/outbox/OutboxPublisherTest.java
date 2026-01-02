package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.application.outbox.MessageProducer;
import kr.hhplus.be.server.application.outbox.OutboxPublisher;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * OutboxPublisher 단위 테스트
 * 
 * Mock 기반 - 발행 로직 검증
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private MessageProducer messageProducer;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxRepository, messageProducer);
    }

    @Test
    @DisplayName("발행 성공 시 PUBLISHED 상태로 변경된다")
    void publishSuccess_StatusPublished() {
        // Given
        Outbox outbox = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");
        when(outboxRepository.findByStatusAndRetryCountLessThan(any(), anyInt()))
                .thenReturn(List.of(outbox));

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(messageProducer).send("ORDER_CREATED", "{}");
        verify(outboxRepository).save(outbox);
    }

    @Test
    @DisplayName("발행 실패 시 retryCount가 증가한다")
    void publishFail_RetryCountIncremented() {
        // Given
        Outbox outbox = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");
        when(outboxRepository.findByStatusAndRetryCountLessThan(any(), anyInt()))
                .thenReturn(List.of(outbox));
        doThrow(new RuntimeException("Connection failed"))
                .when(messageProducer).send(any(), any());

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getErrorMessage()).contains("Connection failed");
    }

    @Test
    @DisplayName("3회 실패 시 FAILED 상태로 변경된다")
    void publishFail3Times_StatusFailed() {
        // Given
        Outbox outbox = new Outbox("ORDER_CREATED", UUID.randomUUID(), "{}");
        outbox.incrementRetry(); // 1
        outbox.incrementRetry(); // 2
        when(outboxRepository.findByStatusAndRetryCountLessThan(any(), anyInt()))
                .thenReturn(List.of(outbox));
        doThrow(new RuntimeException("Connection failed"))
                .when(messageProducer).send(any(), any());

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        assertThat(outbox.getRetryCount()).isEqualTo(3);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("PENDING 이벤트가 없으면 아무것도 하지 않는다")
    void noPendingEvents_DoNothing() {
        // Given
        when(outboxRepository.findByStatusAndRetryCountLessThan(any(), anyInt()))
                .thenReturn(List.of());

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        verify(messageProducer, never()).send(any(), any());
    }
}
