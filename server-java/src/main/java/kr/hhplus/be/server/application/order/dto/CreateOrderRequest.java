package kr.hhplus.be.server.application.order.dto;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        UUID userId,
        List<OrderItemRequest> items
) {
}
