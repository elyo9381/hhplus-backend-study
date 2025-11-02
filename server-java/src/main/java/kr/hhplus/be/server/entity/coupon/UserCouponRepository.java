package kr.hhplus.be.server.entity.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCouponRepository extends JpaRepository<UserCouponEntity, UUID> {
    Optional<UserCouponEntity> findByUserIdAndCouponId(UUID userId, UUID couponId);
    boolean existsByUserIdAndCouponId(UUID userId, UUID couponId);
}
