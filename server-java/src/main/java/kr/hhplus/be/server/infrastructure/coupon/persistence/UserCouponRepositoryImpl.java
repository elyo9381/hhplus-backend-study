package kr.hhplus.be.server.infrastructure.coupon.persistence;

import kr.hhplus.be.server.domain.coupon.UserCoupon;
import kr.hhplus.be.server.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository jpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        UserCouponEntity entity = UserCouponEntity.from(userCoupon);
        UserCouponEntity saved = jpaRepository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<UserCoupon> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(UserCouponEntity::toDomain);
    }

    @Override
    public List<UserCoupon> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(UserCouponEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(UUID userId, UUID couponId) {
        return jpaRepository.findByUserIdAndCouponId(userId, couponId)
                .map(UserCouponEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndCouponId(UUID userId, UUID couponId) {
        return jpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public long countByCouponId(UUID couponId) {
        return jpaRepository.countByCouponId(couponId);
    }
}
