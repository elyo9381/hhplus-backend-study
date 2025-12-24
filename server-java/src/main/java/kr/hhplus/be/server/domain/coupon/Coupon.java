package kr.hhplus.be.server.domain.coupon;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Coupon 도메인 모델
 * 
 * 책임:
 * - 쿠폰 수량 관리
 * - 발급 가능 여부 검증
 * - 선착순 발급 시 수량 차감
 */
@Getter
public class Coupon {
    private final UUID id;
    private final String name;
    private final Long discountAmount;
    private final int totalQuantity;
    private int remainingQuantity;
    private CouponStatus status;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 신규 쿠폰 생성
    public Coupon(String name, Long discountAmount, int totalQuantity, 
                  LocalDateTime startAt, LocalDateTime endAt) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.discountAmount = discountAmount;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.status = CouponStatus.ACTIVE;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Entity → Domain 변환용 생성자
    public Coupon(UUID id, String name, Long discountAmount, int totalQuantity,
                  int remainingQuantity, CouponStatus status,
                  LocalDateTime startAt, LocalDateTime endAt,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.discountAmount = discountAmount;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = remainingQuantity;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 쿠폰 발급 (수량 차감)
     */
    public void issue() {
        validateIssuable();
        this.remainingQuantity--;
        if (this.remainingQuantity == 0) {
            this.status = CouponStatus.EXHAUSTED;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 발급 가능 여부 검증
     */
    public void validateIssuable() {
        if (this.status != CouponStatus.ACTIVE) {
            throw new IllegalStateException("쿠폰이 활성 상태가 아닙니다");
        }
        if (this.remainingQuantity <= 0) {
            throw new IllegalStateException("쿠폰 수량이 소진되었습니다");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(this.startAt)) {
            throw new IllegalStateException("쿠폰 발급 기간이 아닙니다");
        }
        if (now.isAfter(this.endAt)) {
            this.status = CouponStatus.EXPIRED;
            throw new IllegalStateException("쿠폰이 만료되었습니다");
        }
    }

    public boolean isIssuable() {
        try {
            validateIssuable();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
