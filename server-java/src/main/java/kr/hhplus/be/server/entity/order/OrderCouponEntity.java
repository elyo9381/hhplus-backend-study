package kr.hhplus.be.server.entity.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCouponEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID userCouponId;

    @Column(nullable = false)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @Builder
    public OrderCouponEntity(UUID id, UUID orderId, UUID userCouponId, BigDecimal discountAmount, LocalDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.userCouponId = userCouponId;
        this.discountAmount = discountAmount;
        this.createdAt = createdAt;
    }
}
