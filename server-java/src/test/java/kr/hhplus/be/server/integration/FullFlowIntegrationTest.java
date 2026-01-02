package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.AbstractIntegrationTest;
import kr.hhplus.be.server.application.coupon.CouponService;
import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.application.product.ProductService;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전체 플로우 통합 테스트
 * 
 * 충전 → 주문 → 결제 → Outbox 저장 검증
 */
class FullFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private PointService pointService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private CouponService couponService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("전체 플로우: 충전 → 주문 → 결제 → Outbox 저장")
    void 전체_플로우_충전_주문_결제_Outbox() {
        // 1. 상품 생성
        ProductEntity product = productService.createProduct(
                "테스트 상품",
                "설명",
                BigDecimal.valueOf(10000),
                100
        );

        // 2. 포인트 충전
        pointService.chargePoint(userId, 50000L);
        Long balanceAfterCharge = pointService.getAvailablePoints(userId);
        assertThat(balanceAfterCharge).isEqualTo(50000L);

        // 3. 주문 생성
        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));
        
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualTo(20000L);

        // 재고 차감 확인
        ProductEntity updatedProduct = productService.getProduct(product.getId());
        assertThat(updatedProduct.getStock()).isEqualTo(98);

        // 4. 결제 실행
        String idempotencyKey = UUID.randomUUID().toString();
        Payment payment = paymentService.executePayment(order.getId(), userId, idempotencyKey);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getAmount()).isEqualTo(20000L);

        // 포인트 차감 확인
        Long balanceAfterPayment = pointService.getAvailablePoints(userId);
        assertThat(balanceAfterPayment).isEqualTo(30000L);

        // 5. Outbox 저장 확인 (ORDER_CREATED, PAYMENT_COMPLETED)
        List<Outbox> outboxes = outboxRepository.findByStatus(OutboxStatus.PENDING);
        assertThat(outboxes).hasSizeGreaterThanOrEqualTo(2);
        
        boolean hasOrderCreated = outboxes.stream()
                .anyMatch(o -> o.getEventType().equals("ORDER_CREATED"));
        boolean hasPaymentCompleted = outboxes.stream()
                .anyMatch(o -> o.getEventType().equals("PAYMENT_COMPLETED"));
        
        assertThat(hasOrderCreated).isTrue();
        assertThat(hasPaymentCompleted).isTrue();
    }

    @Test
    @DisplayName("idempotency_key 중복 요청 시 같은 결과 반환")
    void idempotency_key_중복_요청_같은_결과() {
        // Given
        ProductEntity product = productService.createProduct(
                "테스트 상품",
                "설명",
                BigDecimal.valueOf(10000),
                100
        );
        pointService.chargePoint(userId, 50000L);

        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));

        String idempotencyKey = UUID.randomUUID().toString();

        // When: 같은 idempotencyKey로 2번 요청
        Payment firstPayment = paymentService.executePayment(order.getId(), userId, idempotencyKey);
        Payment secondPayment = paymentService.executePayment(order.getId(), userId, idempotencyKey);

        // Then: 같은 결제 결과 반환
        assertThat(secondPayment.getId()).isEqualTo(firstPayment.getId());
        assertThat(secondPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // 포인트는 1번만 차감
        Long balance = pointService.getAvailablePoints(userId);
        assertThat(balance).isEqualTo(30000L);
    }

    @Test
    @DisplayName("여러 상품 주문 시 재고 일괄 차감")
    void 여러_상품_주문_재고_일괄_차감() {
        // Given
        ProductEntity productA = productService.createProduct(
                "상품 A", "설명", BigDecimal.valueOf(10000), 50
        );
        ProductEntity productB = productService.createProduct(
                "상품 B", "설명", BigDecimal.valueOf(20000), 30
        );
        pointService.chargePoint(userId, 100000L);

        // When
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(productA.getId(), 3),
                new OrderItemRequest(productB.getId(), 2)
        );
        Order order = orderService.createOrder(userId, items);

        // Then
        assertThat(order.getTotalAmount()).isEqualTo(70000L);  // 30000 + 40000

        ProductEntity updatedA = productService.getProduct(productA.getId());
        ProductEntity updatedB = productService.getProduct(productB.getId());
        assertThat(updatedA.getStock()).isEqualTo(47);
        assertThat(updatedB.getStock()).isEqualTo(28);
    }
}
