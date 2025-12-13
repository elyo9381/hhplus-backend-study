package kr.hhplus.be.server.domain.product;

import java.util.UUID;

public record ProductSnapshot(
        UUID productId,
        String productName,
        Long unitPrice
) {
}
