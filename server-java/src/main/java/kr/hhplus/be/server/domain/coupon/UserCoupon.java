package kr.hhplus.be.server.domain.coupon;

import kr.hhplus.be.server.domain.coupon.UserCouponStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserCoupon {
    private UUID id;
    private UUID userId;
    private UUID couponId;
    private UserCouponStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expiredAt;

    public static UserCoupon issue(UUID userId, UUID couponId, LocalDateTime expiredAt) {
        return new UserCoupon(
                UUID.randomUUID(),
                userId,
                couponId,
                UserCouponStatus.AVAILABLE,
                LocalDateTime.now(),
                null,
                expiredAt
        );
    }

    public void use() {
        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new IllegalStateException("사용 가능한 쿠폰이 아닙니다");
        }
        if (LocalDateTime.now().isAfter(expiredAt)) {
            throw new IllegalStateException("만료된 쿠폰입니다");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void reserve() {
        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new IllegalStateException("사용 가능한 쿠폰이 아닙니다");
        }
        this.status = UserCouponStatus.RESERVED;
    }

    public boolean isAvailable() {
        return this.status == UserCouponStatus.AVAILABLE
                && LocalDateTime.now().isBefore(expiredAt);
    }
}
