package kr.hhplus.be.server.presentation.coupon;

import kr.hhplus.be.server.domain.coupon.CouponIssueStatusType;

import java.util.UUID;

public record CouponIssueResponse(
    UUID requestId,
    CouponIssueStatusType status
) {}
