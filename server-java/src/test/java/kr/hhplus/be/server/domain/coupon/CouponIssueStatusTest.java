package kr.hhplus.be.server.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssueStatusTest {

    @Test
    @DisplayName("신규 생성 시 PENDING 상태로 초기화된다")
    void createWithPendingStatus() {
        // given
        UUID requestId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when
        CouponIssueStatus status = new CouponIssueStatus(requestId, couponId, userId);

        // then
        assertThat(status.getRequestId()).isEqualTo(requestId);
        assertThat(status.getCouponId()).isEqualTo(couponId);
        assertThat(status.getUserId()).isEqualTo(userId);
        assertThat(status.getStatus()).isEqualTo(CouponIssueStatusType.PENDING);
        assertThat(status.getRank()).isNull();
        assertThat(status.getFailReason()).isNull();
        assertThat(status.getCreatedAt()).isNotNull();
        assertThat(status.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("성공 상태로 업데이트하면 순위가 설정된다")
    void updateSuccess() {
        // given
        CouponIssueStatus status = new CouponIssueStatus(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );

        // when
        status.updateSuccess(42);

        // then
        assertThat(status.getStatus()).isEqualTo(CouponIssueStatusType.SUCCESS);
        assertThat(status.getRank()).isEqualTo(42);
        assertThat(status.getFailReason()).isNull();
    }

    @Test
    @DisplayName("실패 상태로 업데이트하면 실패 사유가 설정된다")
    void updateFailed() {
        // given
        CouponIssueStatus status = new CouponIssueStatus(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );

        // when
        status.updateFailed("쿠폰 소진");

        // then
        assertThat(status.getStatus()).isEqualTo(CouponIssueStatusType.FAILED);
        assertThat(status.getRank()).isNull();
        assertThat(status.getFailReason()).isEqualTo("쿠폰 소진");
    }

    @Test
    @DisplayName("성공 후 실패로 변경 가능하다")
    void updateSuccessThenFailed() {
        // given
        CouponIssueStatus status = new CouponIssueStatus(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        status.updateSuccess(10);

        // when
        status.updateFailed("시스템 오류");

        // then
        assertThat(status.getStatus()).isEqualTo(CouponIssueStatusType.FAILED);
        assertThat(status.getRank()).isNull();
        assertThat(status.getFailReason()).isEqualTo("시스템 오류");
    }
}
