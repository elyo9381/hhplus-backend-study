package kr.hhplus.be.server.domain.coupon;

import java.util.Optional;
import java.util.UUID;

public interface CouponIssueStatusRepository {
    CouponIssueStatus save(CouponIssueStatus status);
    Optional<CouponIssueStatus> findById(UUID requestId);
}
