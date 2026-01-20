package kr.hhplus.be.server.coupon;

import kr.hhplus.be.server.domain.coupon.*;
import kr.hhplus.be.server.application.coupon.CouponService;
import kr.hhplus.be.server.infrastructure.coupon.CouponRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private CouponService couponService;

    private Coupon testCoupon;
    private UUID couponId;
    private UUID userId;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    @DisplayName("쿠폰 발급 성공 - Redis 발급 + DB 저장")
    void 쿠폰_발급_성공() {
        // given
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(couponRedisRepository.tryIssue(couponId, userId, 100)).thenReturn(true);
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        UserCoupon result = couponService.issueCoupon(couponId, userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCouponId()).isEqualTo(couponId);
        verify(couponRedisRepository).tryIssue(couponId, userId, 100);
        verify(userCouponRepository).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 이미 발급됨 (Redis)")
    void 쿠폰_발급_실패_이미_발급() {
        // given
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(couponRedisRepository.tryIssue(couponId, userId, 100)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 발급받은 쿠폰");

        verify(userCouponRepository, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 수량 소진 (Redis)")
    void 쿠폰_발급_실패_수량_소진() {
        // given
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(couponRedisRepository.tryIssue(couponId, userId, 100))
                .thenThrow(new IllegalStateException("쿠폰 수량이 소진되었습니다"));

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("수량이 소진");

        verify(userCouponRepository, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - DB 저장 실패 시 Redis 롤백")
    void 쿠폰_발급_실패_DB_저장_실패_롤백() {
        // given
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(couponRedisRepository.tryIssue(couponId, userId, 100)).thenReturn(true);
        when(userCouponRepository.save(any(UserCoupon.class)))
                .thenThrow(new RuntimeException("DB 저장 실패"));

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(RuntimeException.class);

        verify(couponRedisRepository).rollback(couponId, userId);
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
    }

    @Test
    @DisplayName("쿠폰 생성 시 Redis 초기화")
    void 쿠폰_생성_Redis_초기화() {
        // given
        LocalDateTime endAt = LocalDateTime.now().plusDays(30);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        couponService.createCoupon("새 쿠폰", 1000L, 50, LocalDateTime.now(), endAt);

        // then
        verify(couponRedisRepository).initCoupon(any(UUID.class), eq(50), eq(endAt));
    }
}
