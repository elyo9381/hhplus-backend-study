package kr.hhplus.be.server.application.coupon;

import java.time.Instant;
import java.util.UUID;

public record CouponIssueRequest(
    UUID requestId,
    UUID couponId, 
    UUID userId, 
    Instant timestamp
) {}
