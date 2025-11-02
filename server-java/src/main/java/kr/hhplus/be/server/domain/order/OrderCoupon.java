package kr.hhplus.be.server.domain.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class OrderCoupon {
    private UUID id;
    private UUID orderId;
    private UUID userCouponId;
    private BigDecimal discountAmount;
    private LocalDateTime createdAt;

    public static OrderCoupon create(UUID orderId, UUID userCouponId, BigDecimal discountAmount) {
        return OrderCoupon.builder()
                .orderId(orderId)
                .userCouponId(userCouponId)
                .discountAmount(discountAmount)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
