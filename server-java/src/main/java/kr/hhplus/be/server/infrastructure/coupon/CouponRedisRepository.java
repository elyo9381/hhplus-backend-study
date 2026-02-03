package kr.hhplus.be.server.infrastructure.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "coupon:lock:";
    private static final String ISSUED_PREFIX = "coupon:issued:";
    private static final String INFO_PREFIX = "coupon:info:";
    private static final String RANK_PREFIX = "coupon:rank:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 쿠폰 발급 시도 (Redis Only - DB 조회 없음)
     * 
     * @return true: 발급 성공, false: 이미 발급됨
     * @throws IllegalStateException 수량 소진, 기간 외, 쿠폰 없음
     */
    public boolean tryIssue(UUID couponId, UUID userId) {
        // 1. Redis에서 쿠폰 정보 조회 (DB 조회 X)
        CouponInfo info = getCouponInfo(couponId);
        if (info == null) {
            throw new IllegalStateException("쿠폰 정보가 없습니다. Redis 캐시를 확인하세요.");
        }

        // 2. 발급 기간 검증
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(info.startAt())) {
            throw new IllegalStateException("쿠폰 발급 기간이 아닙니다");
        }
        if (now.isAfter(info.endAt())) {
            throw new IllegalStateException("쿠폰이 만료되었습니다");
        }

        // 3. 분산락 + 발급
        return tryIssueWithLock(couponId, userId, info.maxQuantity());
    }

    /**
     * 쿠폰 발급 시도 (maxQuantity 직접 전달 - 하위 호환)
     */
    public boolean tryIssue(UUID couponId, UUID userId, int maxQuantity) {
        return tryIssueWithLock(couponId, userId, maxQuantity);
    }

    private boolean tryIssueWithLock(UUID couponId, UUID userId, int maxQuantity) {
        String lockKey = LOCK_PREFIX + couponId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("락 획득 실패 - 요청이 많습니다. 잠시 후 다시 시도해주세요.");
            }
            
            try {
                RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
                String userIdStr = userId.toString();
                
                // 수량 체크
                int currentCount = issuedSet.size();
                if (currentCount >= maxQuantity) {
                    throw new IllegalStateException("쿠폰 수량이 소진되었습니다");
                }
                
                // 발급 (SADD - 원자적)
                boolean added = issuedSet.add(userIdStr);
                if (!added) {
                    return false; // 이미 발급됨
                }
                
                return true;
                
            } finally {
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
     * 쿠폰 정보 조회 (Redis 캐시)
     */
    public CouponInfo getCouponInfo(UUID couponId) {
        RMap<String, String> map = redissonClient.getMap(INFO_PREFIX + couponId);
        if (map.isEmpty()) {
            return null;
        }
        
        return new CouponInfo(
                Integer.parseInt(map.get("maxQuantity")),
                LocalDateTime.parse(map.get("startAt"), DATE_FORMAT),
                LocalDateTime.parse(map.get("endAt"), DATE_FORMAT)
        );
    }

    /**
     * 발급 롤백
     */
    public void rollback(UUID couponId, UUID userId) {
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        issuedSet.remove(userId.toString());
        log.debug("쿠폰 발급 롤백 - couponId: {}, userId: {}", couponId, userId);
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
     * 순위 원자적 증가 (INCR)
     * - 분산 환경에서도 순위 중복 없이 정확하게 부여
     *
     * @return 현재 순위 (1부터 시작)
     */
    public long incrementRank(UUID couponId) {
        return redissonClient.getAtomicLong(RANK_PREFIX + couponId).incrementAndGet();
    }

    /**
     * 현재 순위 조회 (발급된 수량)
     */
    public long getCurrentRank(UUID couponId) {
        return redissonClient.getAtomicLong(RANK_PREFIX + couponId).get();
    }

    /**
     * 쿠폰 Redis 초기화 (쿠폰 생성 시)
     */
    public void initCoupon(UUID couponId, int maxQuantity, LocalDateTime startAt, LocalDateTime endAt) {
        long ttlSeconds = Duration.between(LocalDateTime.now(), endAt.plusDays(1)).getSeconds();
        Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : Duration.ofDays(1);

        // 발급 Set 초기화
        RSet<String> issuedSet = redissonClient.getSet(ISSUED_PREFIX + couponId);
        issuedSet.clear();
        issuedSet.expire(ttl);

        // 순위 카운터 초기화 (0부터 시작, INCR 시 1부터)
        var rankCounter = redissonClient.getAtomicLong(RANK_PREFIX + couponId);
        rankCounter.set(0);
        rankCounter.expire(ttl);

        // 쿠폰 정보 캐싱 (Hash)
        RMap<String, String> infoMap = redissonClient.getMap(INFO_PREFIX + couponId);
        infoMap.put("maxQuantity", String.valueOf(maxQuantity));
        infoMap.put("startAt", startAt.format(DATE_FORMAT));
        infoMap.put("endAt", endAt.format(DATE_FORMAT));
        infoMap.expire(ttl);

        log.info("쿠폰 Redis 초기화 - couponId: {}, maxQuantity: {}, TTL: {}s",
                couponId, maxQuantity, ttl.getSeconds());
    }

    /**
     * 하위 호환용 (endAt만 받는 버전)
     */
    public void initCoupon(UUID couponId, int maxQuantity, LocalDateTime endAt) {
        initCoupon(couponId, maxQuantity, LocalDateTime.now().minusDays(1), endAt);
    }

    /**
     * 쿠폰 Redis 데이터 삭제
     */
    public void deleteCoupon(UUID couponId) {
        redissonClient.getSet(ISSUED_PREFIX + couponId).delete();
        redissonClient.getMap(INFO_PREFIX + couponId).delete();
        redissonClient.getAtomicLong(RANK_PREFIX + couponId).delete();
    }

    /**
     * 쿠폰 정보 DTO
     */
    public record CouponInfo(int maxQuantity, LocalDateTime startAt, LocalDateTime endAt) {}
}
