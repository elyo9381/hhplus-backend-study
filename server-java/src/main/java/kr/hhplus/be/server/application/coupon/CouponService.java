package kr.hhplus.be.server.application.coupon;

import kr.hhplus.be.server.domain.coupon.*;
import kr.hhplus.be.server.infrastructure.coupon.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
     * 2. 트랜잭션 롤백 시 Redis 자동 롤백 (TransactionSynchronization)
     * 3. DB 저장 (UserCoupon + Coupon remainingQuantity)
     * 
     * Source of Truth: Redis (수량 체크)
     * DB: 영속화 + 조회용
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

        // 4. 트랜잭션 롤백 시 Redis 자동 롤백 등록
        registerRedisRollback(couponId, userId);

        try {
            // 5. DB 저장 - UserCoupon
            UserCoupon userCoupon = new UserCoupon(userId, coupon);
            UserCoupon saved = userCouponRepository.save(userCoupon);
            
            // 6. DB 동기화 - Coupon remainingQuantity 차감
            coupon.issue();
            couponRepository.save(coupon);
            
            return saved;
            
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약 위반 - 이미 발급됨
            // TransactionSynchronization이 롤백 처리하므로 여기서는 예외만 던짐
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        }
    }

    /**
     * 트랜잭션 롤백 시 Redis 롤백 등록
     * - 트랜잭션이 어떤 이유로든 롤백되면 Redis도 롤백
     * - 커밋 성공 시에는 아무것도 안함
     */
    private void registerRedisRollback(UUID couponId, UUID userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        couponRedisRepository.rollback(couponId, userId);
                        log.info("트랜잭션 롤백으로 Redis 롤백 완료 - couponId: {}, userId: {}", couponId, userId);
                    } catch (Exception e) {
                        // Redis 롤백 실패 시 로그만 남김 (배치로 정합성 체크 필요)
                        log.error("Redis 롤백 실패 - couponId: {}, userId: {}", couponId, userId, e);
                    }
                }
            }
        });
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
