package kr.hhplus.be.server.domain.coupon;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class CouponIssueStatus {
    private final UUID requestId;
    private final UUID couponId;
    private final UUID userId;
    private CouponIssueStatusType status;
    private Integer rank;
    private String failReason;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 신규 생성 (PENDING 상태)
    public CouponIssueStatus(UUID requestId, UUID couponId, UUID userId) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueStatusType.PENDING;
        this.rank = null;
        this.failReason = null;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Entity → Domain 변환용
    public CouponIssueStatus(UUID requestId, UUID couponId, UUID userId,
                             CouponIssueStatusType status, Integer rank, String failReason,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = status;
        this.rank = rank;
        this.failReason = failReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 상태 업데이트
     */
    public void updateSuccess(int rank) {
        this.status = CouponIssueStatusType.SUCCESS;
        this.rank = rank;
        this.failReason = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateFailed(String failReason) {
        this.status = CouponIssueStatusType.FAILED;
        this.rank = null;
        this.failReason = failReason;
        this.updatedAt = LocalDateTime.now();
    }
}
