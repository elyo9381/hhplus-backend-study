package kr.hhplus.be.server.domain.product;

import java.util.Optional;
import java.util.UUID;

/**
 * Product Repository 인터페이스 (Port)
 */
public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(UUID id);
    Optional<Product> findByIdWithLock(UUID id);  // 비관적 락
}
