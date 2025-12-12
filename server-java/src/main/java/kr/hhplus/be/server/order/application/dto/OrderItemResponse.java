package kr.hhplus.be.server.order.application.dto;

import kr.hhplus.be.server.order.domain.OrderItem;

import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String productName,
        Long unitPrice,
        int quantity,
        Long totalPrice
) {
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getTotalPrice()
        );
    }
}
