package kr.hhplus.be.server.domain.coupon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> findById(UUID id);
    List<UserCoupon> findByUserId(UUID userId);
    Optional<UserCoupon> findByUserIdAndCouponId(UUID userId, UUID couponId);
    boolean existsByUserIdAndCouponId(UUID userId, UUID couponId);
}
