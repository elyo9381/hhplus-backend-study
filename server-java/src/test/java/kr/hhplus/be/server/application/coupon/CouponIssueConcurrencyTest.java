package kr.hhplus.be.server.application.coupon;

import kr.hhplus.be.server.domain.coupon.*;
import kr.hhplus.be.server.TestContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueConcurrencyTest extends TestContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
        registry.add("spring.kafka.bootstrap-servers", TestContainerSupport::getBootstrapServers);
        registry.add("spring.data.redis.host", TestContainerSupport::getRedisHost);
        registry.add("spring.data.redis.port", TestContainerSupport::getRedisPort);
    }

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponIssueStatusRepository couponIssueStatusRepository;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        testCoupon = couponService.createCoupon(
                "동시성 테스트 쿠폰",
                1000L,
                100,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );
    }

    @Test
    @DisplayName("100명이 동시에 발급 요청 시 순위가 정확하게 매겨진다")
    void concurrency_100Users() throws InterruptedException {
        // given
        int userCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);
        List<UUID> requestIds = new CopyOnWriteArrayList<>();

        // when
        IntStream.range(0, userCount).forEach(i -> {
            executor.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    UUID requestId = couponService.issueCoupon(testCoupon.getId(), userId);
                    requestIds.add(requestId);
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executor.shutdown();

        // then - Consumer 처리 대기
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long successCount = requestIds.stream()
                    .map(id -> couponIssueStatusRepository.findById(id).orElseThrow())
                    .filter(status -> status.getStatus() == CouponIssueStatusType.SUCCESS)
                    .count();

            assertThat(successCount).isEqualTo(100);
        });

        // 순위 중복 체크
        List<Integer> ranks = requestIds.stream()
                .map(id -> couponIssueStatusRepository.findById(id).orElseThrow())
                .filter(status -> status.getStatus() == CouponIssueStatusType.SUCCESS)
                .map(CouponIssueStatus::getRank)
                .sorted()
                .toList();

        assertThat(ranks).hasSize(100);
        assertThat(ranks).containsExactlyElementsOf(
                IntStream.rangeClosed(1, 100).boxed().toList()
        );
    }

    @Test
    @DisplayName("수량 10개 쿠폰에 100명이 동시 요청 시 10명만 성공한다")
    void concurrency_limitedCoupon() throws InterruptedException {
        // given
        Coupon limitedCoupon = couponService.createCoupon(
                "한정 쿠폰",
                1000L,
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );

        int userCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);
        List<UUID> requestIds = new CopyOnWriteArrayList<>();

        // when
        IntStream.range(0, userCount).forEach(i -> {
            executor.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    UUID requestId = couponService.issueCoupon(limitedCoupon.getId(), userId);
                    requestIds.add(requestId);
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executor.shutdown();

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long successCount = requestIds.stream()
                    .map(id -> couponIssueStatusRepository.findById(id).orElseThrow())
                    .filter(status -> status.getStatus() == CouponIssueStatusType.SUCCESS)
                    .count();

            long failedCount = requestIds.stream()
                    .map(id -> couponIssueStatusRepository.findById(id).orElseThrow())
                    .filter(status -> status.getStatus() == CouponIssueStatusType.FAILED)
                    .count();

            assertThat(successCount).isEqualTo(10);
            assertThat(failedCount).isEqualTo(90);
        });

        // 실제 DB 저장 확인
        long dbCount = userCouponRepository.countByCouponId(limitedCoupon.getId());
        assertThat(dbCount).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 사용자가 여러 번 요청해도 1번만 성공한다")
    void concurrency_sameUser() throws InterruptedException {
        // given
        UUID userId = UUID.randomUUID();
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch latch = new CountDownLatch(requestCount);
        List<UUID> requestIds = new CopyOnWriteArrayList<>();

        // when
        IntStream.range(0, requestCount).forEach(i -> {
            executor.submit(() -> {
                try {
                    UUID requestId = couponService.issueCoupon(testCoupon.getId(), userId);
                    requestIds.add(requestId);
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executor.shutdown();

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long successCount = requestIds.stream()
                    .map(id -> couponIssueStatusRepository.findById(id).orElseThrow())
                    .filter(status -> status.getStatus() == CouponIssueStatusType.SUCCESS)
                    .count();

            long failedCount = requestIds.stream()
                    .map(id -> couponIssueStatusRepository.findById(id).orElseThrow())
                    .filter(status -> status.getStatus() == CouponIssueStatusType.FAILED)
                    .filter(status -> status.getFailReason().equals("이미 발급된 쿠폰"))
                    .count();

            assertThat(successCount).isEqualTo(1);
            assertThat(failedCount).isEqualTo(9);
        });
    }
}
