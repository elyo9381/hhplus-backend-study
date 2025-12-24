package kr.hhplus.be.server.application.coupon;

import kr.hhplus.be.server.domain.coupon.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 생성
     */
    @Transactional
    public Coupon createCoupon(String name, Long discountAmount, int totalQuantity,
                               LocalDateTime startAt, LocalDateTime endAt) {
        Coupon coupon = new Coupon(name, discountAmount, totalQuantity, startAt, endAt);
        return couponRepository.save(coupon);
    }

    /**
     * 선착순 쿠폰 발급
     * 
     * 비관적 락으로 동시성 제어
     */
    @Transactional
    public UserCoupon issueCoupon(UUID couponId, UUID userId) {
        // 1. 중복 발급 체크
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        }

        // 2. 쿠폰 조회 (비관적 락)
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

        // 3. 발급 (수량 차감)
        coupon.issue();
        couponRepository.save(coupon);

        // 4. 사용자 쿠폰 생성
        UserCoupon userCoupon = new UserCoupon(userId, coupon);
        return userCouponRepository.save(userCoupon);
    }

    /**
     * 쿠폰 조회
     */
    @Transactional(readOnly = true)
    public Coupon getCoupon(UUID couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));
    }

    /**
     * 사용자 쿠폰 목록 조회
     */
    @Transactional(readOnly = true)
    public List<UserCoupon> getUserCoupons(UUID userId) {
        return userCouponRepository.findByUserId(userId);
    }

    /**
     * 사용자 쿠폰 사용
     */
    @Transactional
    public UserCoupon useCoupon(UUID userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));
        
        userCoupon.use();
        return userCouponRepository.save(userCoupon);
    }
}
