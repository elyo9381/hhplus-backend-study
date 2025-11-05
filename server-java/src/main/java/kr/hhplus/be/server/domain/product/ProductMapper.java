package kr.hhplus.be.server.domain.product;

import kr.hhplus.be.server.entity.product.ProductEntity;

public class ProductMapper {

    public static Product toDomain(ProductEntity entity) {
        return new Product(
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

    public static ProductEntity toEntity(Product domain) {
        return new ProductEntity(
                domain.getName(),
                domain.getDescription(),
                domain.getPrice(),
                domain.getStock()
        );
    }

    public static void updateEntity(ProductEntity entity, Product domain) {
        entity.decreaseStock(0); // trigger status update
    }
}
