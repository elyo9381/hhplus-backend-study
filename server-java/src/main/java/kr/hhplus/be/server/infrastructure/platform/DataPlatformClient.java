package kr.hhplus.be.server.infrastructure.platform;

import kr.hhplus.be.server.domain.payment.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataPlatformClient {

    public void sendOrderData(PaymentCompletedEvent event) {
        // Mock API 호출 - 실제로는 외부 데이터 플랫폼 API 호출
        log.info("[DataPlatform] Sending order data: paymentId={}, orderId={}",
                event.paymentId(), event.orderId());
    }
}
