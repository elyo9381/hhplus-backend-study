package kr.hhplus.be.server.infrastructure.platform;

import kr.hhplus.be.server.domain.payment.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformConsumer {

    private final DataPlatformClient dataPlatformClient;

    @KafkaListener(topics = "payment-completed", groupId = "hhplus-server")
    public void consumePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[Kafka Consumer] Payment completed event received: paymentId={}", event.paymentId());
        try {
            dataPlatformClient.sendOrderData(event);
            log.info("[Kafka Consumer] Successfully sent to data platform: paymentId={}", event.paymentId());
        } catch (Exception e) {
            log.error("[Kafka Consumer] Failed to send to data platform: paymentId={}", event.paymentId(), e);
        }
    }
}
