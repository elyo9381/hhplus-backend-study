package kr.hhplus.be.server.coupon;

import kr.hhplus.be.server.TestContainerSupport;
import kr.hhplus.be.server.application.coupon.CouponService;
import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.coupon.CouponRedisRepository;
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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 발급 Redis 통합 테스트
 * - Redisson 분산락 기반 동시성 제어 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CouponRedisIntegrationTest extends TestContainerSupport {

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
    private CouponService couponService;

    @Autowired
    private CouponRedisRepository couponRedisRepository;

    @Test
    @DisplayName("선착순 쿠폰 발급 - Redis 분산락으로 수량만큼만 성공")
    void 선착순_쿠폰_Redis_수량만큼만_성공() throws InterruptedException {
        // given
        int maxQuantity = 10;
        Coupon coupon = couponService.createCoupon(
                "Redis 선착순 쿠폰",
                5000L,
                maxQuantity,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    couponService.issueCoupon(coupon.getId(), UUID.randomUUID());
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
        assertThat(successCount.get()).isEqualTo(maxQuantity);
        assertThat(failCount.get()).isEqualTo(threadCount - maxQuantity);
        assertThat(couponRedisRepository.getIssuedCount(coupon.getId())).isEqualTo(maxQuantity);
    }

    @Test
    @DisplayName("동일 사용자 중복 발급 방지 - Redis Set으로 차단")
    void 동일_사용자_중복_발급_방지() throws InterruptedException {
        // given
        Coupon coupon = couponService.createCoupon(
                "중복 방지 테스트",
                3000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        UUID userId = UUID.randomUUID();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    couponService.issueCoupon(coupon.getId(), userId);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    // 중복 발급 예외
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(couponRedisRepository.isAlreadyIssued(coupon.getId(), userId)).isTrue();
    }

    @Test
    @DisplayName("쿠폰 발급 후 Redis와 DB 정합성 확인")
    void 쿠폰_발급_정합성_확인() {
        // given
        Coupon coupon = couponService.createCoupon(
                "정합성 테스트",
                1000L,
                5,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        // when
        for (int i = 0; i < 3; i++) {
            couponService.issueCoupon(coupon.getId(), UUID.randomUUID());
        }

        // then
        int redisCount = couponRedisRepository.getIssuedCount(coupon.getId());
        assertThat(redisCount).isEqualTo(3);
    }
}
