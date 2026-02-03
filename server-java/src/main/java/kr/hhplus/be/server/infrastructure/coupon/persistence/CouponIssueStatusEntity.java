package kr.hhplus.be.server.infrastructure.coupon.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatus;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatusType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coupon_issue_status",
       indexes = @Index(name = "idx_user_coupon", columnList = "user_id, coupon_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueStatusEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID requestId;

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID couponId;

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponIssueStatusType status;

    @Column
    private Integer rank;

    @Column(length = 100)
    private String failReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public CouponIssueStatusEntity(CouponIssueStatus domain) {
        this.requestId = domain.getRequestId();
        this.couponId = domain.getCouponId();
        this.userId = domain.getUserId();
        this.status = domain.getStatus();
        this.rank = domain.getRank();
        this.failReason = domain.getFailReason();
        this.createdAt = domain.getCreatedAt();
        this.updatedAt = domain.getUpdatedAt();
    }

    public CouponIssueStatus toDomain() {
        return new CouponIssueStatus(
                requestId, couponId, userId,
                status, rank, failReason,
                createdAt, updatedAt
        );
    }
}
