package kr.hhplus.be.server.domain.coupon;

import kr.hhplus.be.server.entity.coupon.UserCouponEntity;

public class UserCouponMapper {

    public static UserCoupon toDomain(UserCouponEntity entity) {
        return new UserCoupon(
                entity.getId(),
                entity.getUserId(),
                entity.getCouponId(),
                entity.getStatus(),
                entity.getIssuedAt(),
                entity.getUsedAt(),
                entity.getExpiredAt()
        );
    }

    public static UserCouponEntity toEntity(UserCoupon domain) {
        return new UserCouponEntity(
                domain.getUserId(),
                domain.getCouponId(),
                domain.getExpiredAt()
        );
    }
}
