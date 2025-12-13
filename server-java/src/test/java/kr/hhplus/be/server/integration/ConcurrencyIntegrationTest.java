package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.application.product.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 동시성 통합 테스트
 * - ADR-018: 비관적 락으로 재고 동시성 제어
 * - ADR-021: productId 정렬로 데드락 방지
 * - ADR-016: 트랜잭션 원자성
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PointService pointService;

    @Test
    void shouldHandleConcurrentOrdersWithPessimisticLock() throws InterruptedException {
        // given: 재고 10개인 상품
        ProductEntity product = productService.createProduct(
                "Product A", "Description", BigDecimal.valueOf(10000), 10
        );
        UUID productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 10개 스레드가 동시에 1개씩 주문
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    OrderItemRequest request = new OrderItemRequest(productId, 1);
                    orderService.createOrder(userId, List.of(request));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then: 10개 모두 성공, 재고 0
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(0);

        ProductEntity updatedProduct = productService.getProduct(productId);
        assertThat(updatedProduct.getStock()).isEqualTo(0);
    }

    @Test
    void shouldFailWhenInsufficientStock() throws InterruptedException {
        // given: 재고 5개인 상품
        ProductEntity product = productService.createProduct(
                "Product A", "Description", BigDecimal.valueOf(10000), 5
        );
        UUID productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 10개 스레드가 동시에 1개씩 주문 (재고는 5개)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    OrderItemRequest request = new OrderItemRequest(productId, 1);
                    orderService.createOrder(userId, List.of(request));
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().equals("Insufficient stock")) {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then: 5개 성공, 5개 실패
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        ProductEntity updatedProduct = productService.getProduct(productId);
        assertThat(updatedProduct.getStock()).isEqualTo(0);
    }

    @Test
    void shouldPreventDeadlockByOrderingProductIds() throws InterruptedException {
        // given: 상품 A, B 각각 재고 10개
        ProductEntity productA = productService.createProduct(
                "Product A", "Desc A", BigDecimal.valueOf(10000), 10
        );
        ProductEntity productB = productService.createProduct(
                "Product B", "Desc B", BigDecimal.valueOf(20000), 10
        );
        UUID productIdA = productA.getId();
        UUID productIdB = productB.getId();

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount * 2);
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 주문1 [A, B], 주문2 [B, A] 동시 실행
        for (int i = 0; i < threadCount; i++) {
            // 주문1: A → B 순서
            executorService.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    List<OrderItemRequest> items = List.of(
                            new OrderItemRequest(productIdA, 1),
                            new OrderItemRequest(productIdB, 1)
                    );
                    orderService.createOrder(userId, items);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });

            // 주문2: B → A 순서 (역순)
            executorService.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    List<OrderItemRequest> items = List.of(
                            new OrderItemRequest(productIdB, 1),
                            new OrderItemRequest(productIdA, 1)
                    );
                    orderService.createOrder(userId, items);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then: 데드락 없이 모두 성공
        assertThat(successCount.get()).isEqualTo(threadCount * 2);

        ProductEntity updatedA = productService.getProduct(productIdA);
        ProductEntity updatedB = productService.getProduct(productIdB);
        assertThat(updatedA.getStock()).isEqualTo(0);
        assertThat(updatedB.getStock()).isEqualTo(0);
    }

    @Test
    void shouldRollbackWhenPaymentFails() throws InterruptedException {
        // given: 상품 생성, 포인트 부족
        ProductEntity product = productService.createProduct(
                "Product A", "Description", BigDecimal.valueOf(10000), 10
        );
        UUID userId = UUID.randomUUID();
        pointService.chargePoint(userId, 5000L); // 부족한 포인트

        // when: 주문 생성
        OrderItemRequest itemRequest = new OrderItemRequest(product.getId(), 2);
        Order order = orderService.createOrder(userId, List.of(itemRequest));

        // 결제 시도 (실패 예상)
        AtomicInteger failCount = new AtomicInteger(0);
        try {
            paymentService.executePayment(order.getId(), userId);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().equals("Insufficient point balance")) {
                failCount.incrementAndGet();
            }
        }

        // then: 결제 실패, 포인트 롤백
        assertThat(failCount.get()).isEqualTo(1);
        Long remainingPoints = pointService.getAvailablePoints(userId);
        assertThat(remainingPoints).isEqualTo(5000L); // 롤백되어 원래대로
    }

    @Test
    void shouldPreventDuplicatePayment() throws InterruptedException {
        // given: 상품 생성, 포인트 충전, 주문 생성
        ProductEntity product = productService.createProduct(
                "Product A", "Description", BigDecimal.valueOf(10000), 10
        );
        UUID userId = UUID.randomUUID();
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
}
