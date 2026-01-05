package kr.hhplus.be.server.infrastructure.product.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.product.ProductStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product JPA Entity
 * 
 * 레이어드 아키텍처 호환: 직접 생성 및 비즈니스 로직 포함
 * 클린 아키텍처 호환: Domain 변환 메서드 제공
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 레이어드 아키텍처용 생성자
    public ProductEntity(String name, String description, BigDecimal price, int stock) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.status = stock > 0 ? ProductStatus.SELLING : ProductStatus.SOLDOUT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 레이어드 아키텍처용 비즈니스 로직
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new IllegalArgumentException("Insufficient stock");
        }
        this.stock -= quantity;
        updateStatus();
    }

    // 레이어드 아키텍처용 비즈니스 로직
    public void increseStock(int quantity) {
        if ( quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.stock += quantity;
        updateStatus();
    }

    private void updateStatus() {
        this.status = this.stock > 0 ? ProductStatus.SELLING : ProductStatus.SOLDOUT;
        this.updatedAt = LocalDateTime.now();
    }

    // 클린 아키텍처용: Domain → Entity 변환
    public static ProductEntity from(Product product) {
        ProductEntity entity = new ProductEntity();
        entity.id = product.getId();
        entity.name = product.getName();
        entity.description = product.getDescription();
        entity.price = BigDecimal.valueOf(product.getPrice());
        entity.stock = product.getStock();
        entity.status = product.getStatus();
        entity.createdAt = product.getCreatedAt();
        entity.updatedAt = product.getUpdatedAt();
        return entity;
    }

    // 클린 아키텍처용: Entity → Domain 변환
    public Product toDomain() {
        return new Product(
            this.id,
            this.name,
            this.description,
            this.price.longValue(),
            this.stock,
            this.status,
            this.createdAt,
            this.updatedAt
        );
    }
}
