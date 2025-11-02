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
public class OrderItemCoupon {
    private UUID id;
    private UUID orderItemId;
    private UUID userCouponId;
    private BigDecimal discountAmount;
    private LocalDateTime createdAt;

    public static OrderItemCoupon create(UUID orderItemId, UUID userCouponId, BigDecimal discountAmount) {
        return OrderItemCoupon.builder()
                .orderItemId(orderItemId)
                .userCouponId(userCouponId)
                .discountAmount(discountAmount)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
