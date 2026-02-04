package kr.hhplus.be.server.infrastructure.coupon.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponEntity, UUID> {
    List<UserCouponEntity> findByUserId(UUID userId);
    Optional<UserCouponEntity> findByUserIdAndCouponId(UUID userId, UUID couponId);
    
    @Query("SELECT COUNT(uc) > 0 FROM UserCouponEntity uc WHERE uc.userId = :userId AND uc.couponId = :couponId")
    boolean existsByUserIdAndCouponId(@Param("userId") UUID userId, @Param("couponId") UUID couponId);
    
    long countByCouponId(UUID couponId);
}
