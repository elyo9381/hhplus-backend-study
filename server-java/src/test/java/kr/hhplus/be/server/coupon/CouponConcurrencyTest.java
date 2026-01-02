package kr.hhplus.be.server.coupon;

import kr.hhplus.be.server.TestContainerSupport;
import kr.hhplus.be.server.application.coupon.CouponService;
import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.CouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 선착순 발급 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class CouponConcurrencyTest extends TestContainerSupport {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
    }

    @Autowired
    private CouponService couponService;

    @Test
    @DisplayName("선착순 쿠폰 발급 - 수량만큼만 성공")
    void 선착순_쿠폰_발급_수량만큼만_성공() throws InterruptedException {
        // Given: 수량 10개인 쿠폰
        Coupon coupon = couponService.createCoupon(
                "선착순 10명 쿠폰",
                5000L,
                10,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        UUID couponId = coupon.getId();

        int threadCount = 20;  // 20명이 동시에 요청
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 20명이 동시에 쿠폰 발급 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    couponService.issueCoupon(couponId, userId);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 10명만 성공, 10명 실패
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);

        // 쿠폰 상태 확인
        Coupon updatedCoupon = couponService.getCoupon(couponId);
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(0);
        assertThat(updatedCoupon.getStatus()).isEqualTo(CouponStatus.EXHAUSTED);
    }

    @Test
    @DisplayName("동일 사용자 중복 발급 방지")
    void 동일_사용자_중복_발급_방지() throws InterruptedException {
        // Given: 수량 100개인 쿠폰
        Coupon coupon = couponService.createCoupon(
                "중복 방지 테스트 쿠폰",
                5000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        UUID couponId = coupon.getId();
        UUID userId = UUID.randomUUID();  // 동일 사용자

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // When: 같은 사용자가 동시에 10번 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(couponId, userId);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("이미 발급받은")) {
                        duplicateCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 1번만 성공, 나머지는 중복 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(9);

        // 쿠폰 수량은 1개만 차감
        Coupon updatedCoupon = couponService.getCoupon(couponId);
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("여러 쿠폰 동시 발급 - 각각 독립적으로 처리")
    void 여러_쿠폰_동시_발급() throws InterruptedException {
        // Given: 2개의 쿠폰, 각각 수량 5개
        Coupon couponA = couponService.createCoupon(
                "쿠폰 A",
                3000L,
                5,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        Coupon couponB = couponService.createCoupon(
                "쿠폰 B",
                5000L,
                5,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        AtomicInteger successA = new AtomicInteger(0);
        AtomicInteger successB = new AtomicInteger(0);

        // When: 각 쿠폰에 10명씩 동시 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(couponA.getId(), UUID.randomUUID());
                    successA.incrementAndGet();
                } catch (IllegalStateException e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });

            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(couponB.getId(), UUID.randomUUID());
                    successB.incrementAndGet();
                } catch (IllegalStateException e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 각 쿠폰 5명씩 성공
        assertThat(successA.get()).isEqualTo(5);
        assertThat(successB.get()).isEqualTo(5);
    }
}
