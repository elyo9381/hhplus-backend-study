package kr.hhplus.be.server.infrastructure.outbox.scheduler;

import kr.hhplus.be.server.application.outbox.MessageProducer;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 이벤트를 주기적으로 발행하는 스케줄러
 * 
 * 동작:
 * 1. 5초마다 실행
 * 2. PENDING 상태의 Outbox 조회 (재시도 3회 미만)
 * 3. MessageProducer로 발행
 * 4. 성공 시 PUBLISHED로 변경
 * 5. 실패 시 재시도 카운트 증가
 * 6. 재시도 3회 초과 시 FAILED로 변경
 */
@Component
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final MessageProducer messageProducer;

    private static final int MAX_RETRY_COUNT = 3;

    public OutboxScheduler(OutboxRepository outboxRepository, MessageProducer messageProducer) {
        this.outboxRepository = outboxRepository;
        this.messageProducer = messageProducer;
    }

    @Scheduled(fixedDelay = 5000) // 5초마다
    @Transactional
    public void publishPendingEvents() {
        List<Outbox> pendingOutboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, MAX_RETRY_COUNT);

        for (Outbox outbox : pendingOutboxes) {
            try {
                // 외부 시스템에 발행
                messageProducer.send(outbox.getEventType(), outbox.getPayload());

                // 발행 완료 표시
                outbox.markAsPublished();
                outboxRepository.save(outbox);

            } catch (Exception e) {
                // 재시도 카운트 증가
                outbox.incrementRetry();
                outbox.setErrorMessage(e.getMessage());

                // 재시도 초과 시 FAILED로 변경
                if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                    outbox.markAsFailed();
                }

                outboxRepository.save(outbox);
            }
        }
    }
}
