package kr.hhplus.be.server.presentation.product;

import java.math.BigDecimal;

public record ProductRequest(
        String name,
        String description,
        BigDecimal price,
        int stock
) {
}
