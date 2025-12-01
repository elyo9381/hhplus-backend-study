package kr.hhplus.be.server.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        ProductStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProductResponse from(ProductEntity entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getStock(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
