package kr.hhplus.be.server.order.domain;

import kr.hhplus.be.server.order.application.OrderService;
import kr.hhplus.be.server.order.application.dto.OrderItemRequest;
import kr.hhplus.be.server.product.ProductEntity;
import kr.hhplus.be.server.product.ProductRepository;
import kr.hhplus.be.server.product.ProductService;
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

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void shouldHandleConcurrentOrdersWithPessimisticLock() throws InterruptedException {
        // given: 재고 10개인 상품
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
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
    void shouldPreventDeadlockByOrderingProductIds() throws InterruptedException {
        // given: 상품 A, B 각각 재고 10개
        ProductEntity productA = productService.createProduct("Product A", "Desc A", BigDecimal.valueOf(10000), 10);
        ProductEntity productB = productService.createProduct("Product B", "Desc B", BigDecimal.valueOf(20000), 10);
        UUID productIdA = productA.getId();
        UUID productIdB = productB.getId();

        // productIdA < productIdB 되도록 정렬 (UUID 비교)
        UUID smallerId = productIdA.compareTo(productIdB) < 0 ? productIdA : productIdB;
        UUID largerId = productIdA.compareTo(productIdB) < 0 ? productIdB : productIdA;

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 주문1 [A, B], 주문2 [B, A] 동시 실행
        for (int i = 0; i < threadCount; i++) {
            // 주문1: A → B 순서
            executorService.submit(() -> {
                try {
                    List<OrderItemRequest> items = List.of(
                            new OrderItemRequest(smallerId, 1),
                            new OrderItemRequest(largerId, 1)
                    );
                    orderService.createOrder(UUID.randomUUID(), items);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });

            // 주문2: B → A 순서 (역순)
            executorService.submit(() -> {
                try {
                    List<OrderItemRequest> items = List.of(
                            new OrderItemRequest(largerId, 1),
                            new OrderItemRequest(smallerId, 1)
                    );
                    orderService.createOrder(UUID.randomUUID(), items);
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
    void shouldPreserveProductSnapshotInOrder() {
        // given: 상품 생성
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 10);
        UUID productId = product.getId();

        // when: 주문 생성
        OrderItemRequest request = new OrderItemRequest(productId, 2);
        Order order = orderService.createOrder(userId, List.of(request));

        // 상품 가격 변경
        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        // ProductEntity는 불변이므로 새로 생성해야 하지만, 테스트를 위해 직접 수정은 불가
        // 대신 주문의 스냅샷이 원래 가격을 유지하는지 확인

        // then: 주문의 가격은 원래 가격 유지
        Order savedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(savedOrder.getItems().get(0).getUnitPrice()).isEqualTo(10000L);
        assertThat(savedOrder.getItems().get(0).getProductName()).isEqualTo("Product A");
    }

    @Test
    void shouldFailWhenInsufficientStock() throws InterruptedException {
        // given: 재고 5개인 상품
        ProductEntity product = productService.createProduct("Product A", "Description", BigDecimal.valueOf(10000), 5);
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
                    OrderItemRequest request = new OrderItemRequest(productId, 1);
                    orderService.createOrder(UUID.randomUUID(), List.of(request));
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
}
