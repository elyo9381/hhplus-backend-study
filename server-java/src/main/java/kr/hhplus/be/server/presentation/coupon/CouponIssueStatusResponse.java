package kr.hhplus.be.server.presentation.coupon;

import kr.hhplus.be.server.domain.coupon.CouponIssueStatusType;

public record CouponIssueStatusResponse(
    CouponIssueStatusType status,
    Integer rank,
    String failReason
) {}
