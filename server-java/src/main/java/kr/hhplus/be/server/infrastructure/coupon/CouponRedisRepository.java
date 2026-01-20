package kr.hhplus.be.server.infrastructure.coupon;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "coupon:lock:";
    private static final String ISSUED_PREFIX = "coupon:issued:";

    /**
     * 쿠폰 발급 시도 (분산락 + Set 기반)
     * 
     * @return true: 발급 성공, false: 이미 발급됨
     * @throws IllegalStateException 수량 소진 또는 락 획득 실패
     */
    public boolean tryIssue(UUID couponId, UUID userId, int maxQuantity) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + couponId);
        
        try {
            // 락 획득 (5초 대기, 10초 유지)
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("락 획득 실패");
            }
            
            try {
                RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
                String userIdStr = userId.toString();
                
                // 1. 중복 체크
                if (issuedSet.contains(userIdStr)) {
                    return false;
                }
                
                // 2. 수량 체크
                if (issuedSet.size() >= maxQuantity) {
                    throw new IllegalStateException("쿠폰 수량이 소진되었습니다");
                }
                
                // 3. 발급
                issuedSet.add(userIdStr);
                return true;
                
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생");
        }
    }

    /**
     * 발급 롤백 (DB 저장 실패 시)
     */
    public void rollback(UUID couponId, UUID userId) {
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        issuedSet.remove(userId.toString());
    }

    /**
     * 이미 발급 여부 확인
     */
    public boolean isAlreadyIssued(UUID couponId, UUID userId) {
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        return issuedSet.contains(userId.toString());
    }

    /**
     * 현재 발급 수량 조회
     */
    public int getIssuedCount(UUID couponId) {
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        return issuedSet.size();
    }

    /**
     * 쿠폰 Redis 데이터 초기화 (쿠폰 생성 시)
     */
    public void initCoupon(UUID couponId, LocalDateTime endAt) {
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        issuedSet.clear();
        
        // TTL: 쿠폰 만료일 + 1일
        long ttlSeconds = Duration.between(LocalDateTime.now(), endAt.plusDays(1)).getSeconds();
        if (ttlSeconds > 0) {
            issuedSet.expire(Duration.ofSeconds(ttlSeconds));
        }
    }

    /**
     * 쿠폰 Redis 데이터 삭제
     */
    public void deleteCoupon(UUID couponId) {
        redissonClient.getSet(ISSUED_PREFIX + couponId).delete();
    }
}
