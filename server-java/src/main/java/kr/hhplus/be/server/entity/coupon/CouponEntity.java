package kr.hhplus.be.server.entity.coupon;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.coupon.ApplyType;
import kr.hhplus.be.server.domain.coupon.CouponStatus;
import kr.hhplus.be.server.domain.coupon.DiscountType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplyType applyType;

    @Column(nullable = false)
    private BigDecimal discountValue;

    @Column(nullable = false)
    private BigDecimal minOrderAmount;

    private BigDecimal maxDiscountAmount;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer issuedQuantity;

    @Column(nullable = false)
    private Boolean isFirstCome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (issuedQuantity == null) {
            issuedQuantity = 0;
        }
        if (isFirstCome == null) {
            isFirstCome = false;
        }
        if (status == null) {
            status = CouponStatus.ACTIVE;
        }
    }

    public CouponEntity(String name, String description, DiscountType discountType, ApplyType applyType,
                        BigDecimal discountValue, BigDecimal minOrderAmount, BigDecimal maxDiscountAmount,
                        Integer totalQuantity, Boolean isFirstCome, LocalDateTime startDate, LocalDateTime endDate) {
        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.applyType = applyType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.totalQuantity = totalQuantity;
        this.isFirstCome = isFirstCome;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void increaseIssuedQuantity() {
        this.issuedQuantity++;
        if (this.issuedQuantity >= this.totalQuantity) {
            this.status = CouponStatus.EXHAUSTED;
        }
    }
}
