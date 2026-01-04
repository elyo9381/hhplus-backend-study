package kr.hhplus.be.server.application.coupon;

import kr.hhplus.be.server.domain.coupon.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
     * 동시성 제어 (3중 방어):
     * 1. 빠른 실패: 락 획득 전 중복 체크 (불필요한 락 대기 방지)
     * 2. 비관적 락: findByIdWithLock()에서 FOR UPDATE (수량 초과 방지)
     * 3. UNIQUE 제약: user_coupons(user_id, coupon_id) 유니크 인덱스 (중복 발급 최종 방어)
     */
    @Transactional
    public UserCoupon issueCoupon(UUID couponId, UUID userId) {
        // 1. 중복 발급 체크 (빠른 실패 - 락 전 검사)
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        }

        // 2. 쿠폰 조회 (비관적 락 - FOR UPDATE)
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

        // 3. 발급 (수량 검증 + 차감)
        coupon.issue();
        couponRepository.save(coupon);

        // 4. 사용자 쿠폰 생성 (UNIQUE constraint로 중복 최종 방어)
        try {
            UserCoupon userCoupon = new UserCoupon(userId, coupon);
            return userCouponRepository.save(userCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        }
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
