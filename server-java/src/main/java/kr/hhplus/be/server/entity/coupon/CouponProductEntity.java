package kr.hhplus.be.server.entity.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "coupon_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponProductEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID couponId;

    @Column(nullable = false)
    private UUID productId;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public CouponProductEntity(UUID couponId, UUID productId) {
        this.couponId = couponId;
        this.productId = productId;
    }
}
