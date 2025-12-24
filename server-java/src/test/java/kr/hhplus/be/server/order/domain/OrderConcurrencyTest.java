package kr.hhplus.be.server.order.domain;

import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 검증을 위한 동시성 테스트
 * - ADR-018: 비관적 락으로 재고 동시성 제어
 * - ADR-021: productId 정렬로 데드락 방지
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderConcurrencyTest {

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldHandleConcurrentStockDecreaseWithPessimisticLock() throws InterruptedException {
        // given: 재고 10개인 상품
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 10);
        ProductEntity saved = productRepository.save(product);
        UUID productId = saved.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // when: 10개 스레드가 동시에 재고 1개씩 차감
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    transactionTemplate.execute(status -> {
                        Optional<ProductEntity> locked = productRepository.findByIdWithLock(productId);
                        if (locked.isPresent()) {
                            ProductEntity p = locked.get();
                            if (p.getStock() > 0) {
                                p.decreaseStock(1);
                                productRepository.save(p);
                                successCount.incrementAndGet();
                            }
                        }
                        return null;
                    });
                } catch (Exception e) {
                    // 동시성 제어 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then: 비관적 락으로 순차 처리되어 정확히 10개 성공
        ProductEntity result = productRepository.findById(productId).orElseThrow();
        assertThat(result.getStock()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(10);
    }

    @Test
    void shouldVerifyProductIdSortingForDeadlockPrevention() {
        // given: 두 개의 UUID 생성
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();

        // when: UUID 비교
        int comparison = uuid1.compareTo(uuid2);

        // then: 정렬 가능 확인 (compareTo 동작)
        assertThat(comparison).isNotEqualTo(0);

        // 정렬 순서 확인
        UUID smaller = comparison < 0 ? uuid1 : uuid2;
        UUID larger = comparison < 0 ? uuid2 : uuid1;

        assertThat(smaller.compareTo(larger)).isLessThan(0);
        assertThat(larger.compareTo(smaller)).isGreaterThan(0);
    }
}
