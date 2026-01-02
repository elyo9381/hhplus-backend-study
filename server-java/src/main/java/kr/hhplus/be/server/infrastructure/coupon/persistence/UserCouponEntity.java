package kr.hhplus.be.server.infrastructure.coupon.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.coupon.UserCoupon;
import kr.hhplus.be.server.domain.coupon.UserCouponStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_coupons", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(nullable = false)
    private String couponName;

    @Column(nullable = false)
    private Long discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserCouponStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column
    private LocalDateTime usedAt;

    public static UserCouponEntity from(UserCoupon userCoupon) {
        UserCouponEntity entity = new UserCouponEntity();
        entity.id = userCoupon.getId();
        entity.userId = userCoupon.getUserId();
        entity.couponId = userCoupon.getCouponId();
        entity.couponName = userCoupon.getCouponName();
        entity.discountAmount = userCoupon.getDiscountAmount();
        entity.status = userCoupon.getStatus();
        entity.issuedAt = userCoupon.getIssuedAt();
        entity.expiredAt = userCoupon.getExpiredAt();
        entity.usedAt = userCoupon.getUsedAt();
        return entity;
    }

    public UserCoupon toDomain() {
        return new UserCoupon(
            this.id,
            this.userId,
            this.couponId,
            this.couponName,
            this.discountAmount,
            this.status,
            this.issuedAt,
            this.expiredAt,
            this.usedAt
        );
    }
}
