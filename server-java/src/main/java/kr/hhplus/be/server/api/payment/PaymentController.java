package kr.hhplus.be.server.api.payment;

import kr.hhplus.be.server.entity.order.OrderEntity;
import kr.hhplus.be.server.entity.order.OrderRepository;
import kr.hhplus.be.server.entity.payment.PaymentEntity;
import kr.hhplus.be.server.entity.payment.PaymentRepository;
import kr.hhplus.be.server.domain.payment.PaymentType;
import kr.hhplus.be.server.entity.point.UserPointEntity;
import kr.hhplus.be.server.entity.point.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserPointRepository userPointRepository;

    @PostMapping
    @Transactional
    public PaymentEntity processPayment(@RequestBody ProcessPaymentRequest request) {
        OrderEntity order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다"));

        BigDecimal pointAmount = request.getPointAmount() != null ? request.getPointAmount() : BigDecimal.ZERO;

        if (pointAmount.compareTo(BigDecimal.ZERO) > 0) {
            UserPointEntity userPoint = userPointRepository.findByUserId(order.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("포인트를 찾을 수 없습니다"));
            userPoint.use(pointAmount);
        }

        BigDecimal paymentAmount = order.getFinalAmount().subtract(pointAmount);
        order.markAsPaid(paymentAmount, pointAmount);

        PaymentEntity payment = new PaymentEntity(order.getId(), paymentAmount, pointAmount, PaymentType.PAYMENT);
        return paymentRepository.save(payment);
    }
}
