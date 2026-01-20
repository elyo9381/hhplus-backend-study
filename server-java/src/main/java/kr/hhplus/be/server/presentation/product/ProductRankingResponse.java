package kr.hhplus.be.server.presentation.product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRankingResponse(
        int rank,
        UUID productId,
        String productName,
        BigDecimal price,
        long orderCount
) {}
