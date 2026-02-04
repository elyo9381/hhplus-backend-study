package kr.hhplus.be.server.point;

import kr.hhplus.be.server.TestContainerSupport;
import kr.hhplus.be.server.application.point.PointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포인트 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class PointConcurrencyTest extends TestContainerSupport {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
        registry.add("spring.data.redis.host", TestContainerSupport::getRedisHost);
        registry.add("spring.data.redis.port", TestContainerSupport::getRedisPort);
        registry.add("spring.kafka.bootstrap-servers", TestContainerSupport::getBootstrapServers);
    }

    @Autowired
    private PointService pointService;

    @Test
    @DisplayName("동시 포인트 사용 - 잔액만큼만 성공")
    void 동시_포인트_사용_잔액만큼만_성공() throws InterruptedException {
        // Given: 10000 포인트 충전
        UUID userId = UUID.randomUUID();
        pointService.chargePoint(userId, 10000L);

        int threadCount = 10;
        Long useAmount = 2000L;  // 각 스레드가 2000원씩 사용 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10스레드가 동시에 2000원씩 사용 (총 20000원 시도)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("Insufficient")) {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 5번만 성공 (10000 / 2000 = 5)
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        // 잔액 0원
        Long remainingPoints = pointService.getAvailablePoints(userId);
        assertThat(remainingPoints).isEqualTo(0L);
    }

    @Test
    @DisplayName("동시 포인트 충전 - 모두 성공")
    void 동시_포인트_충전_모두_성공() throws InterruptedException {
        // Given
        UUID userId = UUID.randomUUID();

        int threadCount = 10;
        Long chargeAmount = 1000L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 10스레드가 동시에 1000원씩 충전
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 모두 성공, 총 10000원
        assertThat(successCount.get()).isEqualTo(10);

        Long totalPoints = pointService.getAvailablePoints(userId);
        assertThat(totalPoints).isEqualTo(10000L);
    }

    @Test
    @DisplayName("동시 충전과 사용 - 정합성 유지")
    void 동시_충전과_사용_정합성_유지() throws InterruptedException {
        // Given: 초기 5000 포인트
        UUID userId = UUID.randomUUID();
        pointService.chargePoint(userId, 5000L);

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount * 2);
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        AtomicInteger chargeSuccess = new AtomicInteger(0);
        AtomicInteger useSuccess = new AtomicInteger(0);

        // When: 충전(1000원)과 사용(1000원) 동시 실행
        for (int i = 0; i < threadCount; i++) {
            // 충전
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, 1000L);
                    chargeSuccess.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });

            // 사용
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, 1000L);
                    useSuccess.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    // 잔액 부족 시 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 충전은 모두 성공
        assertThat(chargeSuccess.get()).isEqualTo(5);

        // 최종 잔액 = 초기(5000) + 충전(5000) - 사용(useSuccess * 1000)
        Long expectedBalance = 5000L + 5000L - (useSuccess.get() * 1000L);
        Long actualBalance = pointService.getAvailablePoints(userId);
        assertThat(actualBalance).isEqualTo(expectedBalance);
    }
}
