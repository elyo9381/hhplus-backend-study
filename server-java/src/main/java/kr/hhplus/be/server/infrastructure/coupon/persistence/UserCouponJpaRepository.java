package kr.hhplus.be.server.infrastructure.coupon.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponEntity, UUID> {
    List<UserCouponEntity> findByUserId(UUID userId);
    Optional<UserCouponEntity> findByUserIdAndCouponId(UUID userId, UUID couponId);
    boolean existsByUserIdAndCouponId(UUID userId, UUID couponId);
}
