package kr.hhplus.be.server.domain.coupon;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UserCoupon 도메인 모델
 * 
 * 사용자에게 발급된 쿠폰
 */
@Getter
public class UserCoupon {
    private final UUID id;
    private final UUID userId;
    private final UUID couponId;
    private final String couponName;
    private final Long discountAmount;
    private UserCouponStatus status;
    private final LocalDateTime issuedAt;
    private final LocalDateTime expiredAt;
    private LocalDateTime usedAt;

    // 신규 발급
    public UserCoupon(UUID userId, Coupon coupon) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.couponId = coupon.getId();
        this.couponName = coupon.getName();
        this.discountAmount = coupon.getDiscountAmount();
        this.status = UserCouponStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
        this.expiredAt = coupon.getEndAt();
        this.usedAt = null;
    }

    // Entity → Domain 변환용 생성자
    public UserCoupon(UUID id, UUID userId, UUID couponId, String couponName,
                      Long discountAmount, UserCouponStatus status,
                      LocalDateTime issuedAt, LocalDateTime expiredAt, LocalDateTime usedAt) {
        this.id = id;
        this.userId = userId;
        this.couponId = couponId;
        this.couponName = couponName;
        this.discountAmount = discountAmount;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiredAt = expiredAt;
        this.usedAt = usedAt;
    }

    /**
     * 쿠폰 사용
     */
    public void use() {
        if (this.status != UserCouponStatus.ISSUED) {
            throw new IllegalStateException("사용 가능한 쿠폰이 아닙니다");
        }
        if (LocalDateTime.now().isAfter(this.expiredAt)) {
            this.status = UserCouponStatus.EXPIRED;
            throw new IllegalStateException("만료된 쿠폰입니다");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public boolean isUsable() {
        return this.status == UserCouponStatus.ISSUED 
               && LocalDateTime.now().isBefore(this.expiredAt);
    }
}
