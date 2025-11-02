package kr.hhplus.be.server.domain.order;

import kr.hhplus.be.server.entity.order.OrderItemCouponEntity;

public class OrderItemCouponMapper {

    public static OrderItemCoupon toDomain(OrderItemCouponEntity entity) {
        if (entity == null) {
            return null;
        }
        return OrderItemCoupon.builder()
                .id(entity.getId())
                .orderItemId(entity.getOrderItemId())
                .userCouponId(entity.getUserCouponId())
                .discountAmount(entity.getDiscountAmount())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static OrderItemCouponEntity toEntity(OrderItemCoupon domain) {
        if (domain == null) {
            return null;
        }
        return OrderItemCouponEntity.builder()
                .id(domain.getId())
                .orderItemId(domain.getOrderItemId())
                .userCouponId(domain.getUserCouponId())
                .discountAmount(domain.getDiscountAmount())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
