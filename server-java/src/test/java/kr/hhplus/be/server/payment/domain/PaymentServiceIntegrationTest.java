package kr.hhplus.be.server.payment.domain;

import kr.hhplus.be.server.AbstractIntegrationTest;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderRepository;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentRepository;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.application.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PointService pointService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void shouldCompleteOrderToPaymentFlow() {
        // given: 상품 생성 및 포인트 충전
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
        pointService.chargePoint(userId, 50000L);
        String idempotencyKey = UUID.randomUUID().toString();

        // when: 주문 생성
        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));

        // 결제 실행
        Payment payment = paymentService.executePayment(order.getId(), userId, idempotencyKey);

        // then: 결제 성공, 주문 상태 변경
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getAmount()).isEqualTo(20000L);
        assertThat(payment.getIdempotencyKey()).isEqualTo(idempotencyKey);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        Long remainingPoints = pointService.getAvailablePoints(userId);
        assertThat(remainingPoints).isEqualTo(30000L);
    }

    @Test
    void shouldReturnSamePaymentForDuplicateIdempotencyKey() {
        // given: 상품 생성 및 포인트 충전
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
        pointService.chargePoint(userId, 50000L);
        String idempotencyKey = UUID.randomUUID().toString();

        // when: 주문 생성 및 첫 번째 결제
        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));
        Payment firstPayment = paymentService.executePayment(order.getId(), userId, idempotencyKey);

        // 같은 idempotencyKey로 재요청
        Payment secondPayment = paymentService.executePayment(order.getId(), userId, idempotencyKey);

        // then: 같은 결제 결과 반환
        assertThat(secondPayment.getId()).isEqualTo(firstPayment.getId());
        assertThat(secondPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // 포인트는 1번만 차감
        Long remainingPoints = pointService.getAvailablePoints(userId);
        assertThat(remainingPoints).isEqualTo(30000L);
    }

    @Test
    void shouldRollbackWhenInsufficientPoints() {
        // given: 상품 생성, 포인트 부족
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
        pointService.chargePoint(userId, 5000L); // 부족한 포인트
        String idempotencyKey = UUID.randomUUID().toString();

        // when: 주문 생성
        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));

        // then: 결제 실패 (포인트 부족)
        assertThatThrownBy(() -> paymentService.executePayment(order.getId(), userId, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient point balance");

        // 주문 상태는 PENDING 유지
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);

        // 포인트는 차감되지 않음 (롤백)
        Long remainingPoints = pointService.getAvailablePoints(userId);
        assertThat(remainingPoints).isEqualTo(5000L);
    }

    @Test
    void shouldPreventDuplicatePaymentForSameOrder() throws InterruptedException {
        // given: 상품 생성, 포인트 충전, 주문 생성
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
        pointService.chargePoint(userId, 100000L);

        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 동시에 같은 주문에 대해 다른 idempotencyKey로 결제 시도
        for (int i = 0; i < threadCount; i++) {
            String idempotencyKey = UUID.randomUUID().toString();
            executorService.submit(() -> {
                try {
                    paymentService.executePayment(order.getId(), userId, idempotencyKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 중복 결제 또는 주문 상태 변경으로 인한 실패
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then: 1개만 성공, 나머지는 중복 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(4);

        // 포인트는 1번만 차감
        Long remainingPoints = pointService.getAvailablePoints(userId);
        assertThat(remainingPoints).isEqualTo(80000L);
    }

    @Test
    void shouldFailWhenOrderAlreadyPaid() {
        // given: 상품 생성, 포인트 충전, 주문 생성 및 결제 완료
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
        pointService.chargePoint(userId, 100000L);

        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));
        String firstKey = UUID.randomUUID().toString();
        paymentService.executePayment(order.getId(), userId, firstKey);

        // when & then: 다른 idempotencyKey로 재결제 시도
        String secondKey = UUID.randomUUID().toString();
        assertThatThrownBy(() -> paymentService.executePayment(order.getId(), userId, secondKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment already exists for this order");
    }
}
