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
     * - DB와 Redis 동시 초기화 (정합성 보장)
     * - Redis 초기화 실패 시 트랜잭션 롤백
     */
    @Transactional
    public Coupon createCoupon(String name, Long discountAmount, int totalQuantity,
                               LocalDateTime startAt, LocalDateTime endAt) {
        // 1. DB 저장
        Coupon coupon = new Coupon(name, discountAmount, totalQuantity, startAt, endAt);
        Coupon saved = couponRepository.save(coupon);
        
        // 2. Redis 초기화 (DB와 동일한 값)
        //    실패 시 RuntimeException → 트랜잭션 롤백
        try {
            couponRedisRepository.initCoupon(
                    saved.getId(),
                    totalQuantity,
                    startAt,
                    endAt
            );
        } catch (Exception e) {
            log.error("Redis 초기화 실패 - couponId: {}", saved.getId(), e);
            throw new RuntimeException("쿠폰 생성 실패: Redis 초기화 오류", e);
        }
        
        return saved;
    }

    /**
     * 선착순 쿠폰 발급 (Redis Only - DB 조회 없음)
     * 
     * 동시성 제어:
     * 1. Redis에서 쿠폰 정보 조회 (DB 조회 X)
     * 2. Redis 분산락으로 원자적 발급 (중복 + 수량 + 기간 체크)
     * 3. 트랜잭션 롤백 시 Redis 자동 롤백
     * 4. DB 저장 (UserCoupon + Coupon remainingQuantity)
     */
    @Transactional
    public UserCoupon issueCoupon(UUID couponId, UUID userId) {
        // 1. Redis 분산락 기반 발급 (DB 조회 없이 Redis만 사용)
        boolean issued = couponRedisRepository.tryIssue(couponId, userId);
        if (!issued) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        }

        // 2. 트랜잭션 롤백 시 Redis 자동 롤백 등록
        registerRedisRollback(couponId, userId);

        // 3. DB 저장 (쿠폰 정보는 여기서 조회 - 저장용)
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

        try {
            // 4. DB 저장 - UserCoupon
            UserCoupon userCoupon = new UserCoupon(userId, coupon);
            UserCoupon saved = userCouponRepository.save(userCoupon);
            
            // 5. DB 동기화 - Coupon remainingQuantity 차감
            coupon.issue();
            couponRepository.save(coupon);
            
            return saved;
            
        } catch (DataIntegrityViolationException e) {
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
