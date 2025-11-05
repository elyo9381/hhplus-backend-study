package kr.hhplus.be.server.domain.order;

import kr.hhplus.be.server.entity.order.OrderCouponEntity;

public class OrderCouponMapper {

    public static OrderCoupon toDomain(OrderCouponEntity entity) {
        if (entity == null) {
            return null;
        }
        return OrderCoupon.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .userCouponId(entity.getUserCouponId())
                .discountAmount(entity.getDiscountAmount())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static OrderCouponEntity toEntity(OrderCoupon domain) {
        if (domain == null) {
            return null;
        }
        return OrderCouponEntity.builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .userCouponId(domain.getUserCouponId())
                .discountAmount(domain.getDiscountAmount())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
