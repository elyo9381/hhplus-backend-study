package kr.hhplus.be.server.coupon;

import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.CouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CouponTest {

    @Test
    @DisplayName("쿠폰 생성 시 ACTIVE 상태")
    void 쿠폰_생성_시_ACTIVE_상태() {
        // Given & When
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        // Then
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(coupon.getRemainingQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("쿠폰 발급 시 수량 차감")
    void 쿠폰_발급_시_수량_차감() {
        // Given
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        // When
        coupon.issue();

        // Then
        assertThat(coupon.getRemainingQuantity()).isEqualTo(99);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("마지막 쿠폰 발급 시 EXHAUSTED 상태로 변경")
    void 마지막_쿠폰_발급_시_EXHAUSTED_상태로_변경() {
        // Given
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                1,  // 수량 1개
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        // When
        coupon.issue();

        // Then
        assertThat(coupon.getRemainingQuantity()).isEqualTo(0);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.EXHAUSTED);
    }

    @Test
    @DisplayName("수량 소진 시 발급 실패")
    void 수량_소진_시_발급_실패() {
        // Given
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                1,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        coupon.issue();  // 수량 소진

        // When & Then
        assertThatThrownBy(coupon::issue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("활성 상태가 아닙니다");
    }

    @Test
    @DisplayName("발급 기간 전 발급 실패")
    void 발급_기간_전_발급_실패() {
        // Given
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().plusDays(1),  // 내일부터 시작
                LocalDateTime.now().plusDays(30)
        );

        // When & Then
        assertThatThrownBy(coupon::issue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 기간이 아닙니다");
    }

    @Test
    @DisplayName("발급 기간 후 발급 실패")
    void 발급_기간_후_발급_실패() {
        // Given
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(30),
                LocalDateTime.now().minusDays(1)  // 어제 종료
        );

        // When & Then
        assertThatThrownBy(coupon::issue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("만료되었습니다");
    }

    @Test
    @DisplayName("발급 가능 여부 확인")
    void 발급_가능_여부_확인() {
        // Given
        Coupon activeCoupon = new Coupon(
                "활성 쿠폰",
                1000L,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        Coupon exhaustedCoupon = new Coupon(
                "소진 쿠폰",
                1000L,
                1,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        exhaustedCoupon.issue();

        // When & Then
        assertThat(activeCoupon.isIssuable()).isTrue();
        assertThat(exhaustedCoupon.isIssuable()).isFalse();
    }
}
