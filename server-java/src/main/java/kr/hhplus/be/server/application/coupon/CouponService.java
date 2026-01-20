package kr.hhplus.be.server.application.coupon;

import kr.hhplus.be.server.domain.coupon.*;
import kr.hhplus.be.server.infrastructure.coupon.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponRedisRepository couponRedisRepository;

    /**
     * 쿠폰 생성
     */
    @Transactional
    public Coupon createCoupon(String name, Long discountAmount, int totalQuantity,
                               LocalDateTime startAt, LocalDateTime endAt) {
        Coupon coupon = new Coupon(name, discountAmount, totalQuantity, startAt, endAt);
        Coupon saved = couponRepository.save(coupon);
        
        // Redis 초기화 (수량 캐싱 포함)
        couponRedisRepository.initCoupon(saved.getId(), totalQuantity, endAt);
        
        return saved;
    }

    /**
     * 선착순 쿠폰 발급 (Redis 분산락 기반)
     * 
     * 동시성 제어:
     * 1. Redis 분산락으로 원자적 발급 (중복 + 수량 체크)
     * 2. DB 저장 (UserCoupon + Coupon remainingQuantity 동기화)
     * 3. 실패 시 Redis 롤백
     */
    @Transactional
    public UserCoupon issueCoupon(UUID couponId, UUID userId) {
        // 1. 쿠폰 정보 조회
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

        // 2. 발급 기간 검증
        coupon.validateIssuable();

        // 3. Redis 분산락 기반 발급 시도
        boolean issued = couponRedisRepository.tryIssue(couponId, userId, coupon.getTotalQuantity());
        if (!issued) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        }

        try {
            // 4. DB 저장 - UserCoupon
            UserCoupon userCoupon = new UserCoupon(userId, coupon);
            UserCoupon saved = userCouponRepository.save(userCoupon);
            
            // 5. DB 동기화 - Coupon remainingQuantity 차감
            coupon.issue();
            couponRepository.save(coupon);
            
            return saved;
            
        } catch (DataIntegrityViolationException e) {
            couponRedisRepository.rollback(couponId, userId);
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        } catch (Exception e) {
            couponRedisRepository.rollback(couponId, userId);
            throw e;
        }
    }

    /**
     * 선착순 쿠폰 발급 (DB 비관적 락 - Fallback용)
     */
    @Transactional
    public UserCoupon issueCouponWithDbLock(UUID couponId, UUID userId) {
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        }

        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

        coupon.issue();
        couponRepository.save(coupon);

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
