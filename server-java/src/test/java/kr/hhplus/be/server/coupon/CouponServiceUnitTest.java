package kr.hhplus.be.server.coupon;

import kr.hhplus.be.server.domain.coupon.*;
import kr.hhplus.be.server.application.coupon.CouponService;
import kr.hhplus.be.server.infrastructure.coupon.CouponRedisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CouponService 단위 테스트 (Mock 기반)
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceUnitTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponRedisRepository couponRedisRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private CouponIssueStatusRepository couponIssueStatusRepository;

    private CouponService couponService;

    private Coupon testCoupon;
    private UUID couponId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(kafkaTemplate, couponRepository, userCouponRepository, couponRedisRepository, couponIssueStatusRepository);
        
        couponId = UUID.randomUUID();
        userId = UUID.randomUUID();
        testCoupon = new Coupon(
                couponId,
                "테스트 쿠폰",
                5000L,
                100,
                50,
                CouponStatus.ACTIVE,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        
        // 트랜잭션 동기화 활성화 (단위 테스트용)
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("쿠폰 발급 성공 (비동기) - Kafka 발행")
    void 쿠폰_발급_성공() {
        // given
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(couponIssueStatusRepository.save(any(CouponIssueStatus.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        UUID requestId = couponService.issueCoupon(couponId, userId);

        // then
        assertThat(requestId).isNotNull();
        verify(couponRepository).findById(couponId);
        verify(couponIssueStatusRepository).save(any(CouponIssueStatus.class));
        verify(kafkaTemplate).send(eq("coupon-issue-request"), eq(couponId.toString()), any());
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 쿠폰 없음")
    void 쿠폰_발급_실패_쿠폰_없음() {
        // given
        when(couponRepository.findById(couponId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
        
        verify(couponIssueStatusRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("발급 상태 조회 성공")
    void 발급_상태_조회_성공() {
        // given
        UUID requestId = UUID.randomUUID();
        CouponIssueStatus status = new CouponIssueStatus(requestId, couponId, userId);
        status.updateSuccess(42);
        
        when(couponIssueStatusRepository.findById(requestId)).thenReturn(Optional.of(status));

        // when
        CouponIssueStatus result = couponService.getIssueStatus(requestId);

        // then
        assertThat(result.getRequestId()).isEqualTo(requestId);
        assertThat(result.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);
        assertThat(result.getRank()).isEqualTo(42);
    }

    @Test
    @DisplayName("발급 상태 조회 실패 - 요청 없음")
    void 발급_상태_조회_실패() {
        // given
        UUID requestId = UUID.randomUUID();
        when(couponIssueStatusRepository.findById(requestId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.getIssueStatus(requestId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("요청을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("쿠폰 생성 시 Redis 초기화")
    void 쿠폰_생성_Redis_초기화() {
        // given
        LocalDateTime startAt = LocalDateTime.now();
        LocalDateTime endAt = LocalDateTime.now().plusDays(30);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        couponService.createCoupon("새 쿠폰", 1000L, 50, startAt, endAt);

        // then
        verify(couponRedisRepository).initCoupon(any(UUID.class), eq(50), eq(startAt), eq(endAt));
    }
}
