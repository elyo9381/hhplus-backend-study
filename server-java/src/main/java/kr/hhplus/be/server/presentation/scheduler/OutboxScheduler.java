package kr.hhplus.be.server.presentation.scheduler;

import kr.hhplus.be.server.application.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 이벤트를 주기적으로 발행하는 Primary Adapter (Scheduler)
 * 
 * 역할:
 * - 5초마다 OutboxPublisher UseCase를 트리거
 * - 비즈니스 로직은 포함하지 않음 (단순 트리거 역할)
 */
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxPublisher outboxPublisher;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        outboxPublisher.publishPendingEvents();
    }
}
