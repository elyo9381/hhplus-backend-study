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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueKafkaIntegrationTest extends TestContainerSupport {

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
        // 테스트 쿠폰 생성
        testCoupon = couponService.createCoupon(
                "테스트 쿠폰",
                1000L,
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );
    }

    @Test
    @DisplayName("Producer → Kafka → Consumer 전체 플로우가 정상 동작한다")
    void kafkaIntegration_success() throws InterruptedException {
        // given
        UUID userId = UUID.randomUUID();
        
        // Consumer가 준비될 때까지 대기
        Thread.sleep(3000);

        // when
        UUID requestId = couponService.issueCoupon(testCoupon.getId(), userId);

        // then - Consumer가 처리할 때까지 대기
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            CouponIssueStatus status = couponIssueStatusRepository.findById(requestId).orElseThrow();
            assertThat(status.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);
            assertThat(status.getRank()).isEqualTo(1);
        });

        // UserCoupon 저장 확인
        assertThat(userCouponRepository.existsByUserIdAndCouponId(userId, testCoupon.getId())).isTrue();
    }

    @Test
    @DisplayName("여러 사용자가 순차적으로 발급받으면 순위가 정확하게 매겨진다")
    void kafkaIntegration_multipleUsers() throws InterruptedException {
        // given
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID user3 = UUID.randomUUID();
        
        Thread.sleep(3000);

        // when
        UUID requestId1 = couponService.issueCoupon(testCoupon.getId(), user1);
        UUID requestId2 = couponService.issueCoupon(testCoupon.getId(), user2);
        UUID requestId3 = couponService.issueCoupon(testCoupon.getId(), user3);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            CouponIssueStatus status1 = couponIssueStatusRepository.findById(requestId1).orElseThrow();
            CouponIssueStatus status2 = couponIssueStatusRepository.findById(requestId2).orElseThrow();
            CouponIssueStatus status3 = couponIssueStatusRepository.findById(requestId3).orElseThrow();

            assertThat(status1.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);
            assertThat(status2.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);
            assertThat(status3.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);

            assertThat(status1.getRank()).isEqualTo(1);
            assertThat(status2.getRank()).isEqualTo(2);
            assertThat(status3.getRank()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("중복 발급 시 FAILED 상태로 처리된다")
    void kafkaIntegration_duplicated() throws InterruptedException {
        // given
        UUID userId = UUID.randomUUID();
        
        Thread.sleep(3000);

        // when
        UUID requestId1 = couponService.issueCoupon(testCoupon.getId(), userId);
        UUID requestId2 = couponService.issueCoupon(testCoupon.getId(), userId);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            CouponIssueStatus status1 = couponIssueStatusRepository.findById(requestId1).orElseThrow();
            CouponIssueStatus status2 = couponIssueStatusRepository.findById(requestId2).orElseThrow();

            assertThat(status1.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);
            assertThat(status2.getStatus()).isEqualTo(CouponIssueStatusType.FAILED);
            assertThat(status2.getFailReason()).isEqualTo("이미 발급된 쿠폰");
        });
    }

    @Test
    @DisplayName("수량 초과 시 FAILED 상태로 처리된다")
    void kafkaIntegration_soldOut() throws InterruptedException {
        // given - 수량 2개 쿠폰 생성
        Coupon limitedCoupon = couponService.createCoupon(
                "한정 쿠폰",
                1000L,
                2,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID user3 = UUID.randomUUID();
        
        Thread.sleep(3000);

        // when
        UUID requestId1 = couponService.issueCoupon(limitedCoupon.getId(), user1);
        UUID requestId2 = couponService.issueCoupon(limitedCoupon.getId(), user2);
        UUID requestId3 = couponService.issueCoupon(limitedCoupon.getId(), user3);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            CouponIssueStatus status1 = couponIssueStatusRepository.findById(requestId1).orElseThrow();
            CouponIssueStatus status2 = couponIssueStatusRepository.findById(requestId2).orElseThrow();
            CouponIssueStatus status3 = couponIssueStatusRepository.findById(requestId3).orElseThrow();

            assertThat(status1.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);
            assertThat(status2.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);
            assertThat(status3.getStatus()).isEqualTo(CouponIssueStatusType.FAILED);
            assertThat(status3.getFailReason()).isEqualTo("쿠폰 소진");
        });
    }
}
