package kr.hhplus.be.server.order.application.dto;

import java.util.UUID;

public record OrderItemRequest(
        UUID productId,
        int quantity
) {
}
