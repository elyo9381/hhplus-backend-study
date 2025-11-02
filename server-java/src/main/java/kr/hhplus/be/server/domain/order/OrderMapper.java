package kr.hhplus.be.server.domain.order;

import kr.hhplus.be.server.entity.order.OrderEntity;
import kr.hhplus.be.server.entity.order.OrderItemEntity;

import java.util.stream.Collectors;

public class OrderMapper {

    public static Order toDomain(OrderEntity entity) {
        return new Order(
                entity.getId(),
                entity.getUserId(),
                entity.getTotalAmount(),
                entity.getDiscountAmount(),
                entity.getFinalAmount(),
                entity.getPaidAmount(),
                entity.getPointAmount(),
                entity.getRemainingAmount(),
                entity.getStatus(),
                entity.getOrderItems().stream()
                        .map(OrderItemMapper::toDomain)
                        .collect(Collectors.toList()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static OrderEntity toEntity(Order domain) {
        OrderEntity entity = new OrderEntity(domain.getUserId());
        
        for (OrderItem item : domain.getOrderItems()) {
            OrderItemEntity itemEntity = OrderItemMapper.toEntity(item);
            entity.addOrderItem(itemEntity);
        }
        
        return entity;
    }
}
