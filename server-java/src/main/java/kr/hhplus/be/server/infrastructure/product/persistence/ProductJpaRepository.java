package kr.hhplus.be.server.infrastructure.product.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Product JPA Repository
 */
public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    /**
     * 비관적 락을 사용한 조회
     * SELECT ... FOR UPDATE
     * 
     * 용도: 재고 차감 시 동시성 제어
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id = :id")
    Optional<ProductEntity> findByIdWithLock(@Param("id") UUID id);
}
