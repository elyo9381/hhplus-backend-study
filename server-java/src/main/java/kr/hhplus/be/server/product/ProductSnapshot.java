package kr.hhplus.be.server.product;

import java.util.UUID;

public record ProductSnapshot(
        UUID productId,
        String productName,
        Long unitPrice
) {
}
