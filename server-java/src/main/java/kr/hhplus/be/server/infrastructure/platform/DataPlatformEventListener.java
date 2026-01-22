package kr.hhplus.be.server.infrastructure.platform;

import kr.hhplus.be.server.domain.payment.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformEventListener {

    private final DataPlatformClient dataPlatformClient;

    @Async("dataPlatformExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[EventListener] Payment completed event received: paymentId={}", event.paymentId());
        try {
            dataPlatformClient.sendOrderData(event);
        } catch (Exception e) {
            log.error("[EventListener] Failed to send order data to platform: paymentId={}", event.paymentId(), e);
        }
    }
}
