package kr.hhplus.be.server.domain.product;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product 도메인 모델
 * 
 * 책임:
 * - 재고 관리 (차감, 검증)
 * - 상품 상태 관리 (SELLING, SOLDOUT)
 * - 재고 부족 시 예외 발생
 */
@Getter
public class Product {
    private final UUID id;
    private final String name;
    private final String description;
    private final Long price;
    private Integer stock;
    private ProductStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 신규 상품 생성 시 사용하는 생성자
    public Product(String name, String description, Long price, Integer stock) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.status = stock > 0 ? ProductStatus.SELLING : ProductStatus.SOLDOUT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Entity에서 도메인으로 변환 시 사용하는 생성자
    public Product(UUID id, String name, String description, Long price, 
                   Integer stock, ProductStatus status, 
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 재고 차감
     * 
     * @param quantity 차감할 수량
     * @throws InsufficientStockException 재고 부족 시
     */
    public void decreaseStock(int quantity) {
        validateStock(quantity);
        this.stock -= quantity;
        
        if (this.stock == 0) {
            this.status = ProductStatus.SOLDOUT;
        }
        
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 검증
     * 
     * @param quantity 요청 수량
     * @throws InsufficientStockException 재고 부족 시
     */
    public void validateStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        
        if (this.stock < quantity) {
            throw new InsufficientStockException(
                String.format("재고 부족: 요청=%d, 현재=%d", quantity, this.stock)
            );
        }
    }

    /**
     * 재고 추가 (환불, 재입고 등)
     */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        
        this.stock += quantity;
        
        if (this.stock > 0 && this.status == ProductStatus.SOLDOUT) {
            this.status = ProductStatus.SELLING;
        }
        
        this.updatedAt = LocalDateTime.now();
    }
}
