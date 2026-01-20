package kr.hhplus.be.server.infrastructure.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "coupon:lock:";
    private static final String ISSUED_PREFIX = "coupon:issued:";
    private static final String QUANTITY_PREFIX = "coupon:quantity:";

    /**
     * 쿠폰 발급 시도 (분산락 + 원자적 조회)
     * 
     * 동작 방식:
     * 1. 분산락 획득 (쿠폰 단위)
     * 2. 락 내부에서 원자적으로: 중복 체크 → 수량 체크 → 발급
     * 3. 락 해제
     * 
     * @return true: 발급 성공, false: 이미 발급됨
     * @throws IllegalStateException 수량 소진 또는 락 획득 실패
     */
    public boolean tryIssue(UUID couponId, UUID userId, int maxQuantity) {
        String lockKey = LOCK_PREFIX + couponId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 1. 분산락 획득 (5초 대기, 10초 유지, Watchdog 자동 갱신)
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("락 획득 실패 - 요청이 많습니다. 잠시 후 다시 시도해주세요.");
            }
            
            try {
                return executeIssueWithinLock(couponId, userId, maxQuantity);
            } finally {
                // 락 해제 (본인이 획득한 락만 해제)
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생");
        }
    }

    /**
     * 락 내부에서 실행되는 발급 로직
     * - 분산락이 보장하므로 이 메서드 내부는 단일 스레드로 실행됨
     */
    private boolean executeIssueWithinLock(UUID couponId, UUID userId, int maxQuantity) {
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        String userIdStr = userId.toString();
        
        // 1. 중복 체크 + 발급을 SADD 하나로 처리
        //    SADD는 이미 존재하면 false 반환 (원자적)
        //    하지만 수량 체크를 먼저 해야 하므로 순서 유지
        
        // 2. 수량 체크 (Set 크기 = 발급 수)
        int currentCount = issuedSet.size();
        if (currentCount >= maxQuantity) {
            throw new IllegalStateException("쿠폰 수량이 소진되었습니다");
        }
        
        // 3. 발급 시도 (SADD - 원자적, 이미 존재하면 false)
        boolean added = issuedSet.add(userIdStr);
        if (!added) {
            // 이미 발급됨 (동일 사용자 중복 요청)
            return false;
        }
        
        log.debug("쿠폰 발급 성공 - couponId: {}, userId: {}, 현재 발급 수: {}/{}",
                couponId, userId, currentCount + 1, maxQuantity);
        
        return true;
    }

    /**
     * 쿠폰 발급 시도 (Redis 캐시된 수량 사용 - DB 조회 불필요)
     */
    public boolean tryIssueWithCachedQuantity(UUID couponId, UUID userId) {
        // Redis에서 수량 조회
        Integer maxQuantity = getCachedQuantity(couponId);
        if (maxQuantity == null) {
            throw new IllegalStateException("쿠폰 정보가 Redis에 없습니다. 쿠폰 ID: " + couponId);
        }
        return tryIssue(couponId, userId, maxQuantity);
    }

    /**
     * 발급 롤백 (DB 저장 실패 시)
     */
    public void rollback(UUID couponId, UUID userId) {
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        issuedSet.remove(userId.toString());
        log.debug("쿠폰 발급 롤백 - couponId: {}, userId: {}", couponId, userId);
    }

    /**
     * 이미 발급 여부 확인 (락 없이 조회)
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
     * 캐시된 최대 수량 조회
     */
    public Integer getCachedQuantity(UUID couponId) {
        RBucket<Integer> bucket = redissonClient.getBucket(QUANTITY_PREFIX + couponId);
        return bucket.get();
    }

    /**
     * 쿠폰 Redis 데이터 초기화 (쿠폰 생성 시)
     * - 발급 Set 초기화
     * - 최대 수량 캐싱
     */
    public void initCoupon(UUID couponId, int maxQuantity, LocalDateTime endAt) {
        long ttlSeconds = Duration.between(LocalDateTime.now(), endAt.plusDays(1)).getSeconds();
        Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : Duration.ofDays(1);
        
        // 발급 Set 초기화
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        issuedSet.clear();
        issuedSet.expire(ttl);
        
        // 최대 수량 캐싱
        RBucket<Integer> quantityBucket = redissonClient.getBucket(QUANTITY_PREFIX + couponId);
        quantityBucket.set(maxQuantity, ttl);
        
        log.info("쿠폰 Redis 초기화 - couponId: {}, maxQuantity: {}, TTL: {}s", 
                couponId, maxQuantity, ttl.getSeconds());
    }

    /**
     * 쿠폰 Redis 데이터 삭제
     */
    public void deleteCoupon(UUID couponId) {
        redissonClient.getSet(ISSUED_PREFIX + couponId).delete();
        redissonClient.getBucket(QUANTITY_PREFIX + couponId).delete();
    }
}
