package kr.hhplus.be.server.domain.coupon;

import kr.hhplus.be.server.entity.coupon.CouponEntity;

public class CouponMapper {

    public static Coupon toDomain(CouponEntity entity) {
        return new Coupon(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDiscountType(),
                entity.getApplyType(),
                entity.getDiscountValue(),
                entity.getMinOrderAmount(),
                entity.getMaxDiscountAmount(),
                entity.getTotalQuantity(),
                entity.getIssuedQuantity(),
                entity.getIsFirstCome(),
                entity.getStatus(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getCreatedAt()
        );
    }

    public static CouponEntity toEntity(Coupon domain) {
        return new CouponEntity(
                domain.getName(),
                domain.getDescription(),
                domain.getDiscountType(),
                domain.getApplyType(),
                domain.getDiscountValue(),
                domain.getMinOrderAmount(),
                domain.getMaxDiscountAmount(),
                domain.getTotalQuantity(),
                domain.getIsFirstCome(),
                domain.getStartDate(),
                domain.getEndDate()
        );
    }
}
