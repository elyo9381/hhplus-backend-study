package kr.hhplus.be.server.application.outbox;

import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 발행 비즈니스 로직을 담당하는 UseCase
 * 
 * 책임:
 * - PENDING 상태의 Outbox 조회
 * - 외부 시스템에 메시지 발행
 * - 발행 성공/실패에 따른 상태 변경
 * - 재시도 정책 관리
 */
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final MessageProducer messageProducer;

    private static final int MAX_RETRY_COUNT = 3;

    @Transactional
    public void publishPendingEvents() {
        List<Outbox> pendingOutboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, MAX_RETRY_COUNT);

        for (Outbox outbox : pendingOutboxes) {
            try {
                messageProducer.send(outbox.getEventType(), outbox.getPayload());
                outbox.markAsPublished();
                outboxRepository.save(outbox);

            } catch (Exception e) {
                outbox.incrementRetry();
                outbox.setErrorMessage(e.getMessage());

                if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                    outbox.markAsFailed();
                }

                outboxRepository.save(outbox);
            }
        }
    }
}
