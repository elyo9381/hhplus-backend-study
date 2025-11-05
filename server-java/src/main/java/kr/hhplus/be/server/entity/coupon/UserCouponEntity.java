package kr.hhplus.be.server.entity.coupon;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.coupon.UserCouponStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserCouponStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    private LocalDateTime expiredAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = UserCouponStatus.AVAILABLE;
        }
    }

    public UserCouponEntity(UUID userId, UUID couponId, LocalDateTime expiredAt) {
        this.userId = userId;
        this.couponId = couponId;
        this.expiredAt = expiredAt;
    }

    public void use() {
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void reserve() {
        this.status = UserCouponStatus.RESERVED;
    }
}
