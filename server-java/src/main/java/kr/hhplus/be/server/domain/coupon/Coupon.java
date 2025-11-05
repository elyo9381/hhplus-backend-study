package kr.hhplus.be.server.domain.coupon;

import kr.hhplus.be.server.domain.coupon.ApplyType;
import kr.hhplus.be.server.domain.coupon.CouponStatus;
import kr.hhplus.be.server.domain.coupon.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Coupon {
    private UUID id;
    private String name;
    private String description;
    private DiscountType discountType;
    private ApplyType applyType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private Integer totalQuantity;
    private Integer issuedQuantity;
    private Boolean isFirstCome;
    private CouponStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime createdAt;

    public void issue() {
        if (this.issuedQuantity >= this.totalQuantity) {
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다");
        }
        this.issuedQuantity++;
        if (this.issuedQuantity >= this.totalQuantity) {
            this.status = CouponStatus.EXHAUSTED;
        }
    }

    public boolean isAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return this.status == CouponStatus.ACTIVE
                && now.isAfter(startDate)
                && now.isBefore(endDate)
                && this.issuedQuantity < this.totalQuantity;
    }

    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        if (orderAmount.compareTo(minOrderAmount) < 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;
        if (discountType == DiscountType.FIXED) {
            discount = discountValue;
        } else {
            discount = orderAmount.multiply(discountValue).divide(BigDecimal.valueOf(100));
        }

        if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
            discount = maxDiscountAmount;
        }

        return discount;
    }
}
