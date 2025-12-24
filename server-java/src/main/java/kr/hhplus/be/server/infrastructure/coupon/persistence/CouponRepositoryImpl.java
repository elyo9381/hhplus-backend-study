package kr.hhplus.be.server.infrastructure.coupon.persistence;

import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository jpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        CouponEntity entity = CouponEntity.from(coupon);
        CouponEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Coupon> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(CouponEntity::toDomain);
    }

    @Override
    public Optional<Coupon> findByIdWithLock(UUID id) {
        return jpaRepository.findByIdWithLock(id)
                .map(CouponEntity::toDomain);
    }
}
