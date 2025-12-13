package kr.hhplus.be.server.application.payment;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderRepository;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentRepository;
import kr.hhplus.be.server.domain.payment.PointPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PointPort pointPort;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          PointPort pointPort) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.pointPort = pointPort;
    }

    @Transactional
    public Payment executePayment(UUID orderId, UUID userId) {
        // 1. 주문 조회 및 락 획득 (동시성 제어)
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // 2. 결제 중복 체크
        Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            throw new IllegalStateException("Payment already exists");
        }

        // 3. 사용자 검증
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User mismatch");
        }

        // 4. 주문 상태 검증
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not pending");
        }

        // 5. 포인트 사용
        Long amount = order.getFinalAmount();
        pointPort.usePoint(userId, amount);

        // 6. 결제 생성 및 완료
        Payment payment = new Payment(orderId, userId, amount, amount);
        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);

        // 7. 주문 상태 변경
        order.completePayment(amount);
        orderRepository.save(order);

        return savedPayment;
    }
}
