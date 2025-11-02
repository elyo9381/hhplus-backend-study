package kr.hhplus.be.server.domain.order;

import kr.hhplus.be.server.entity.order.OrderItemEntity;

public class OrderItemMapper {

    public static OrderItem toDomain(OrderItemEntity entity) {
        return new OrderItem(
                entity.getId(),
                entity.getProductId(),
                entity.getProductName(),
                entity.getUnitPrice(),
                entity.getQuantity(),
                entity.getTotalPrice(),
                entity.getDiscountAmount(),
                entity.getFinalPrice()
        );
    }

    public static OrderItemEntity toEntity(OrderItem domain) {
        return new OrderItemEntity(
                domain.getProductId(),
                domain.getProductName(),
                domain.getUnitPrice(),
                domain.getQuantity()
        );
    }
}
