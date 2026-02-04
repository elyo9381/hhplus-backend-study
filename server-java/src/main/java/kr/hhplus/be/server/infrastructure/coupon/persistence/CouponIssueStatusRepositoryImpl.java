package kr.hhplus.be.server.infrastructure.coupon.persistence;

import kr.hhplus.be.server.domain.coupon.CouponIssueStatus;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CouponIssueStatusRepositoryImpl implements CouponIssueStatusRepository {

    private final CouponIssueStatusJpaRepository jpaRepository;

    @Override
    public CouponIssueStatus save(CouponIssueStatus status) {
        CouponIssueStatusEntity entity = new CouponIssueStatusEntity(status);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<CouponIssueStatus> findById(UUID requestId) {
        return jpaRepository.findById(requestId)
                .map(CouponIssueStatusEntity::toDomain);
    }
}
