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
@Table(name = "order_item_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemCouponEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orderItemId;

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
    public OrderItemCouponEntity(UUID id, UUID orderItemId, UUID userCouponId, BigDecimal discountAmount, LocalDateTime createdAt) {
        this.id = id;
        this.orderItemId = orderItemId;
        this.userCouponId = userCouponId;
        this.discountAmount = discountAmount;
        this.createdAt = createdAt;
    }
}
