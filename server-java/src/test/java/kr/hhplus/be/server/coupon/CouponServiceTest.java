package kr.hhplus.be.server.coupon;

import kr.hhplus.be.server.application.coupon.CouponService;
import kr.hhplus.be.server.domain.coupon.*;
import kr.hhplus.be.server.infrastructure.coupon.CouponRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponRedisRepository couponRedisRepository;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(couponRepository, userCouponRepository, couponRedisRepository);
        // 트랜잭션 동기화 활성화 (단위 테스트용)
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @Test
    @DisplayName("쿠폰 발급 성공 - Redis 발급 + DB 저장")
    void 쿠폰_발급_성공() {
        // Given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        when(couponRedisRepository.tryIssue(couponId, userId)).thenReturn(true);
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(inv -> inv.getArgument(0));
        when(couponRepository.save(any(Coupon.class))).thenReturn(coupon);

        // When
        UserCoupon result = couponService.issueCoupon(couponId, userId);

        // Then
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCouponId()).isEqualTo(coupon.getId());
        assertThat(result.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        verify(couponRedisRepository).tryIssue(couponId, userId);
        verify(couponRepository).findById(couponId);
        verify(userCouponRepository).save(any(UserCoupon.class));
        
        // cleanup
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("중복 발급 시 예외 발생 - Redis에서 거부")
    void 중복_발급_시_예외_발생() {
        // Given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(couponRedisRepository.tryIssue(couponId, userId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 발급받은 쿠폰입니다");

        verify(couponRepository, never()).findById(any());
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 시 예외 발생")
    void 존재하지_않는_쿠폰_발급_시_예외_발생() {
        // Given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(couponRedisRepository.tryIssue(couponId, userId)).thenReturn(true);
        when(couponRepository.findById(couponId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
        
        // cleanup
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("수량 소진된 쿠폰 발급 시 예외 발생 - Redis에서 거부")
    void 수량_소진된_쿠폰_발급_시_예외_발생() {
        // Given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Redis에서 수량 소진으로 발급 거부
        when(couponRedisRepository.tryIssue(couponId, userId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(IllegalStateException.class);
    }
}
