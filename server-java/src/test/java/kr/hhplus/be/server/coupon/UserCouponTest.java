package kr.hhplus.be.server.coupon;

import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.UserCoupon;
import kr.hhplus.be.server.domain.coupon.UserCouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserCouponTest {

    @Test
    @DisplayName("사용자 쿠폰 발급 시 ISSUED 상태")
    void 사용자_쿠폰_발급_시_ISSUED_상태() {
        // Given
        UUID userId = UUID.randomUUID();
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        // When
        UserCoupon userCoupon = new UserCoupon(userId, coupon);

        // Then
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCoupon.getUserId()).isEqualTo(userId);
        assertThat(userCoupon.getCouponId()).isEqualTo(coupon.getId());
        assertThat(userCoupon.getDiscountAmount()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("쿠폰 사용 시 USED 상태로 변경")
    void 쿠폰_사용_시_USED_상태로_변경() {
        // Given
        UUID userId = UUID.randomUUID();
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        UserCoupon userCoupon = new UserCoupon(userId, coupon);

        // When
        userCoupon.use();

        // Then
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        assertThat(userCoupon.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 사용한 쿠폰 재사용 실패")
    void 이미_사용한_쿠폰_재사용_실패() {
        // Given
        UUID userId = UUID.randomUUID();
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        UserCoupon userCoupon = new UserCoupon(userId, coupon);
        userCoupon.use();

        // When & Then
        assertThatThrownBy(userCoupon::use)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사용 가능한 쿠폰이 아닙니다");
    }

    @Test
    @DisplayName("사용 가능 여부 확인")
    void 사용_가능_여부_확인() {
        // Given
        UUID userId = UUID.randomUUID();
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        UserCoupon issuedCoupon = new UserCoupon(userId, coupon);
        
        UserCoupon usedCoupon = new UserCoupon(userId, coupon);
        usedCoupon.use();

        // When & Then
        assertThat(issuedCoupon.isUsable()).isTrue();
        assertThat(usedCoupon.isUsable()).isFalse();
    }
}
