package kr.hhplus.be.server.infrastructure.coupon.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponIssueStatusJpaRepository extends JpaRepository<CouponIssueStatusEntity, UUID> {
}
