package kr.hhplus.be.server.domain.coupon;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(UUID id);
    Optional<Coupon> findByIdWithLock(UUID id);  // 비관적 락
}
