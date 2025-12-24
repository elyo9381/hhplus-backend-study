package kr.hhplus.be.server.infrastructure.coupon.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.CouponStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long discountAmount;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static CouponEntity from(Coupon coupon) {
        CouponEntity entity = new CouponEntity();
        entity.id = coupon.getId();
        entity.name = coupon.getName();
        entity.discountAmount = coupon.getDiscountAmount();
        entity.totalQuantity = coupon.getTotalQuantity();
        entity.remainingQuantity = coupon.getRemainingQuantity();
        entity.status = coupon.getStatus();
        entity.startAt = coupon.getStartAt();
        entity.endAt = coupon.getEndAt();
        entity.createdAt = coupon.getCreatedAt();
        entity.updatedAt = coupon.getUpdatedAt();
        return entity;
    }

    public Coupon toDomain() {
        return new Coupon(
            this.id,
            this.name,
            this.discountAmount,
            this.totalQuantity,
            this.remainingQuantity,
            this.status,
            this.startAt,
            this.endAt,
            this.createdAt,
            this.updatedAt
        );
    }
}
