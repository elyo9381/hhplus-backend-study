package kr.hhplus.be.server.infrastructure.coupon.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Coupon JPA Repository
 * 
 * 동시성 제어: 비관적 락 (PESSIMISTIC_WRITE) + UNIQUE 제약
 * - 선착순 쿠폰 발급 시 초과 발급 방지
 * - 동일 사용자 중복 발급 방지 (UNIQUE constraint)
 */
public interface CouponJpaRepository extends JpaRepository<CouponEntity, UUID> {

    /**
     * 비관적 락을 사용한 쿠폰 조회
     * SELECT ... FOR UPDATE
     * 
     * 용도: 쿠폰 발급 시 동시성 제어 (수량 초과 방지)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponEntity c WHERE c.id = :id")
    Optional<CouponEntity> findByIdWithLock(@Param("id") UUID id);
}
