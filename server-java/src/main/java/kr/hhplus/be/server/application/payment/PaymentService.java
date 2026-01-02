package kr.hhplus.be.server.application.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderRepository;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PointPort pointPort;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          PointPort pointPort,
                          OutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.pointPort = pointPort;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Payment executePayment(UUID orderId, UUID userId, String idempotencyKey) {
        // 1. idempotencyKey로 기존 결제 조회 (중복 요청 처리)
        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            return existingByKey.get();  // 기존 결제 결과 반환
        }

        // 2. 주문 조회 및 락 획득 (동시성 제어)
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // 3. orderId로 결제 중복 체크
        Optional<Payment> existingByOrder = paymentRepository.findByOrderId(orderId);
        if (existingByOrder.isPresent()) {
            throw new IllegalStateException("Payment already exists for this order");
        }

        // 4. 사용자 검증
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User mismatch");
        }

        // 5. 주문 상태 검증
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not pending");
        }

        // 6. 포인트 사용
        Long amount = order.getFinalAmount();
        pointPort.usePoint(userId, amount);

        // 7. 결제 생성 및 완료
        Payment payment = new Payment(orderId, userId, idempotencyKey, amount, amount);
        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);

        // 8. 주문 상태 변경 (같은 트랜잭션)
        order.completePayment(amount);
        orderRepository.save(order);

        // 9. Outbox 이벤트 저장
        Outbox outbox = new Outbox(
                "PAYMENT_COMPLETED",
                savedPayment.getId(),
                toJson(Map.of(
                        "paymentId", savedPayment.getId().toString(),
                        "orderId", orderId.toString(),
                        "userId", userId.toString(),
                        "amount", amount,
                        "orderStatus", "PAID"
                ))
        );
        outboxRepository.save(outbox);

        return savedPayment;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
