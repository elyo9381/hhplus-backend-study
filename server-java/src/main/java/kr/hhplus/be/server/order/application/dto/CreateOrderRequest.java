package kr.hhplus.be.server.order.application.dto;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        UUID userId,
        List<OrderItemRequest> items
) {
}
