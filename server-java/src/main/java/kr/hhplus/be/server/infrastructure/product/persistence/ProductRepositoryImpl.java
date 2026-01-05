package kr.hhplus.be.server.infrastructure.product.persistence;

import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * ProductRepository 구현체 (Adapter)
 * 
 * 역할:
 * - Domain Repository 인터페이스 구현
 * - JPA Repository 사용
 * - Entity ↔ Domain 변환
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    @Override
    @Transactional
    public Product save(Product product) {
        ProductEntity entity = ProductEntity.from(product);
        ProductEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public Optional<Product> findByIdWithLock(UUID id) {
        return jpaRepository.findByIdWithLock(id)
                .map(ProductEntity::toDomain);
    }
}
