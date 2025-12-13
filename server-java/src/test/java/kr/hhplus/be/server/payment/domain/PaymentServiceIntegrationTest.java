package kr.hhplus.be.server.payment.domain;

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
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentServiceIntegrationTest {

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

        // when: 주문 생성
        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));

        // 결제 실행
        Payment payment = paymentService.executePayment(order.getId(), userId);

        // then: 결제 성공, 주문 상태 변경
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getAmount()).isEqualTo(20000L);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        Long remainingPoints = pointService.getAvailablePoints(userId);
        assertThat(remainingPoints).isEqualTo(30000L);
    }

    @Test
    void shouldRollbackWhenInsufficientPoints() {
        // given: 상품 생성, 포인트 부족
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
        pointService.chargePoint(userId, 5000L); // 부족한 포인트

        // when: 주문 생성
        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));

        // then: 결제 실패 (포인트 부족)
        assertThatThrownBy(() -> paymentService.executePayment(order.getId(), userId))
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
    void shouldPreventDuplicatePayment() throws InterruptedException {
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

        // when: 동시에 같은 주문에 대해 결제 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    paymentService.executePayment(order.getId(), userId);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage().equals("Payment already exists")) {
                        failCount.incrementAndGet();
                    }
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
    void shouldFailWhenOrderNotPending() {
        // given: 상품 생성, 포인트 충전, 주문 생성 및 결제 완료
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
        pointService.chargePoint(userId, 100000L);

        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));
        paymentService.executePayment(order.getId(), userId);

        // when & then: 이미 결제된 주문에 대해 재결제 시도
        assertThatThrownBy(() -> paymentService.executePayment(order.getId(), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment already exists");
    }
}
