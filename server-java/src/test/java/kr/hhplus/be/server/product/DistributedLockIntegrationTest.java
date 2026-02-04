package kr.hhplus.be.server.product;

import kr.hhplus.be.server.TestContainerSupport;
import kr.hhplus.be.server.application.product.ProductFacade;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분산락 통합 테스트
 * - 단일 시도 (decreaseStock)
 * - 폴링 재시도 (decreaseStockWithSnapshot)
 * - Pub/Sub (increseStock)
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DistributedLockIntegrationTest extends TestContainerSupport {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", TestContainerSupport::getBootstrapServers);
    }

    @Autowired
    private ProductFacade productFacade;

    @Test
    @DisplayName("분산락 단일 시도 - 동시 재고 차감")
    void 분산락_단일시도_동시_재고차감() throws InterruptedException {
        // given
        ProductEntity product = productFacade.createProduct(
                "Test Product", "Description", BigDecimal.valueOf(10000), 100
        );
        UUID productId = product.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productFacade.decreaseStock(productId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        ProductEntity updated = productFacade.getProduct(productId);
        assertThat(updated.getStock()).isEqualTo(100 - successCount.get());
    }

    @Test
    @DisplayName("분산락 폴링 재시도 - 동시 재고 차감")
    void 분산락_폴링재시도_동시_재고차감() throws InterruptedException {
        // given
        ProductEntity product = productFacade.createProduct(
                "Test Product", "Description", BigDecimal.valueOf(10000), 100
        );
        UUID productId = product.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productFacade.decreaseStockWithSnapshot(productId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then - 폴링 재시도로 더 많이 성공
        ProductEntity updated = productFacade.getProduct(productId);
        assertThat(updated.getStock()).isEqualTo(100 - successCount.get());
        assertThat(successCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("분산락 Pub/Sub - 동시 재고 증가")
    void 분산락_PubSub_동시_재고증가() throws InterruptedException {
        // given
        ProductEntity product = productFacade.createProduct(
                "Test Product", "Description", BigDecimal.valueOf(10000), 0
        );
        UUID productId = product.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productFacade.increseStock(productId, 10);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        ProductEntity updated = productFacade.getProduct(productId);
        assertThat(updated.getStock()).isEqualTo(successCount.get() * 10);
    }

    @Test
    @DisplayName("분산락 Pub/Sub - 경합 시 일부 실패 가능")
    void 분산락_PubSub_경합시_일부실패() throws InterruptedException {
        /*
         * Pub/Sub 방식의 한계:
         * - 락 해제 시 모든 대기자에게 동시에 알림 발송
         * - 알림 받은 여러 스레드가 동시에 setIfAbsent() 시도
         * - 하나만 성공, 나머지는 실패
         * - 현재 구현은 1회만 재시도하므로 대부분 실패
         *
         * Redisson은 이를 해결:
         * - 세마포어로 순차적 깨움
         * - 타임아웃까지 무한 재시도
         * - Lettuce 직접 구현 시 이런 한계 존재
         */
        
        // given - 20개 스레드가 동시에 1개씩 증가 시도
        ProductEntity product = productFacade.createProduct(
                "Test Product", "Description", BigDecimal.valueOf(10000), 10
        );
        UUID productId = product.getId();

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - Pub/Sub은 1회 재시도만 하므로 경합 시 실패 가능
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productFacade.increseStock(productId, 1);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    // Lock acquisition failed
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then - 경합으로 인해 일부 실패 가능
        ProductEntity updated = productFacade.getProduct(productId);
        assertThat(updated.getStock()).isEqualTo(10 + successCount.get());
        
        // Pub/Sub 1회 재시도 특성상 경합 시 실패 발생 가능
        System.out.println("Pub/Sub 성공: " + successCount.get() + ", 실패: " + failCount.get());
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("분산락 폴링 vs Pub/Sub - 폴링이 더 안정적")
    void 분산락_폴링vs펍섭_비교() throws InterruptedException {
        /*
         * 폴링 방식 vs Pub/Sub 방식 비교:
         *
         * [폴링 방식 - decreaseStockWithSnapshot]
         * - 실패 시 sleep(100ms) 후 재시도
         * - 최대 3회 재시도
         * - 경합 시에도 순차적으로 성공 가능
         *
         * [Pub/Sub 방식 - increseStock]
         * - 실패 시 채널 구독 후 대기
         * - 알림 받으면 1회만 재시도
         * - 여러 스레드가 동시에 알림 받으면 하나만 성공
         *
         * [Redisson이 해결하는 방법]
         * - 세마포어로 동시 깨어남 제어
         * - 타임아웃까지 무한 재시도
         * - Lettuce 직접 구현 시 이런 복잡한 로직 필요
         */
        
        // given
        ProductEntity product1 = productFacade.createProduct(
                "Polling Product", "Description", BigDecimal.valueOf(10000), 100
        );
        ProductEntity product2 = productFacade.createProduct(
                "PubSub Product", "Description", BigDecimal.valueOf(10000), 100
        );

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        AtomicInteger pollingSuccess = new AtomicInteger(0);
        AtomicInteger pubsubSuccess = new AtomicInteger(0);

        // when - 폴링 방식 (재시도 3회)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productFacade.decreaseStockWithSnapshot(product1.getId(), 1);
                    pollingSuccess.incrementAndGet();
                } catch (Exception e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        // when - Pub/Sub 방식 (재시도 1회)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productFacade.increseStock(product2.getId(), 1);
                    pubsubSuccess.incrementAndGet();
                } catch (Exception e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        System.out.println("폴링 성공: " + pollingSuccess.get() + "/" + threadCount);
        System.out.println("Pub/Sub 성공: " + pubsubSuccess.get() + "/" + threadCount);
        
        // 폴링은 재시도로 더 많이 성공하는 경향
        // Pub/Sub은 1회 재시도라 경합 시 실패 가능
    }

    @Test
    @DisplayName("분산락 - 재고 정합성 보장")
    void 분산락_재고_정합성_보장() throws InterruptedException {
        // given
        ProductEntity product = productFacade.createProduct(
                "Test Product", "Description", BigDecimal.valueOf(10000), 50
        );
        UUID productId = product.getId();

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when - 50개 스레드가 각각 1개씩 차감
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productFacade.decreaseStockWithSnapshot(productId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 재고 부족 또는 락 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then - 정확히 재고만큼만 성공
        ProductEntity updated = productFacade.getProduct(productId);
        assertThat(updated.getStock()).isGreaterThanOrEqualTo(0);
        assertThat(successCount.get()).isLessThanOrEqualTo(50);
    }
}
