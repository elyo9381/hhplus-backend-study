package kr.hhplus.be.server.application.order;

import kr.hhplus.be.server.domain.product.ProductSnapshot;

import java.util.UUID;

/**
 * Product 외부 시스템과의 통신을 위한 Port 인터페이스
 * 의존성 역전 원칙(DIP)을 위해 OrderService의 요구사항으로 정의
 */
public interface ProductPort {
    ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity);
}
