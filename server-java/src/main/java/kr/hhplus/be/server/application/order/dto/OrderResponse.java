package kr.hhplus.be.server.application.order.dto;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID userId,
        List<OrderItemResponse> items,
        Long totalAmount,
        Long finalAmount,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getItems().stream()
                        .map(OrderItemResponse::from)
                        .toList(),
                order.getTotalAmount(),
                order.getFinalAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
