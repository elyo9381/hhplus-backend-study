package kr.hhplus.be.server.order.domain;

import kr.hhplus.be.server.product.ProductSnapshot;

import java.util.UUID;

/**
 * Product 외부 시스템과의 통신을 위한 Port 인터페이스
 * 의존성 역전 원칙(DIP)을 위해 Order 도메인에 정의
 */
public interface ProductPort {
    ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity);
}
