package kr.hhplus.be.server.application.order.dto;

import java.util.UUID;

public record OrderItemRequest(
        UUID productId,
        int quantity
) {
}
