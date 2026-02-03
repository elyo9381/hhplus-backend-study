package kr.hhplus.be.server.application.outbox;

import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private MessageProducer messageProducer;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("PENDING 상태의 Outbox 이벤트를 찾아 메시지를 발행하고 상태를 PUBLISHED로 변경한다")
    void shouldPublishPendingEventsSuccessfully() {
        // given
        Outbox outbox = new Outbox("PAYMENT_COMPLETED", UUID.randomUUID(), "{\"paymentId\": \"123\"}");
        when(outboxRepository.findByStatusAndRetryCountLessThan(eq(OutboxStatus.PENDING), anyInt()))
                .thenReturn(List.of(outbox));

        // when
        outboxPublisher.publishPendingEvents();

        // then
        verify(messageProducer, times(1)).send(eq("PAYMENT_COMPLETED"), anyString());
        verify(outboxRepository, times(1)).save(argThat(o -> o.getStatus() == OutboxStatus.PUBLISHED));
    }

    @Test
    @DisplayName("메시지 발행 실패 시 재시도 횟수를 증가시킨다")
    void shouldIncrementRetryCountOnFailure() {
        // given
        Outbox outbox = new Outbox("PAYMENT_COMPLETED", UUID.randomUUID(), "{\"paymentId\": \"123\"}");
        when(outboxRepository.findByStatusAndRetryCountLessThan(eq(OutboxStatus.PENDING), anyInt()))
                .thenReturn(List.of(outbox));
        
        doThrow(new RuntimeException("Kafka error")).when(messageProducer).send(anyString(), anyString());

        // when
        outboxPublisher.publishPendingEvents();

        // then
        verify(outboxRepository, times(1)).save(argThat(o -> o.getRetryCount() == 1));
    }

    @Test
    @DisplayName("재시도 횟수가 최대치에 도달하면 FAILED 상태로 변경한다")
    void shouldMarkAsFailedWhenMaxRetryReached() {
        // given
        Outbox outbox = new Outbox("PAYMENT_COMPLETED", UUID.randomUUID(), "{\"paymentId\": \"123\"}");
        // 억지로 2번 실패한 상태로 세팅
        outbox.incrementRetry();
        outbox.incrementRetry();
        
        when(outboxRepository.findByStatusAndRetryCountLessThan(eq(OutboxStatus.PENDING), anyInt()))
                .thenReturn(List.of(outbox));
        
        doThrow(new RuntimeException("Kafka error")).when(messageProducer).send(anyString(), anyString());

        // when
        outboxPublisher.publishPendingEvents();

        // then
        verify(outboxRepository, times(1)).save(argThat(o -> o.getStatus() == OutboxStatus.FAILED));
    }
}
