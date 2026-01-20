package kr.hhplus.be.server.infrastructure.product;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ProductRankingRepository {

    private final RedissonClient redissonClient;

    private static final String DAILY_PREFIX = "product:ranking:daily:";
    private static final String WEEKLY_PREFIX = "product:ranking:weekly:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 상품 주문 수량 증가 (일별 + 주별)
     */
    public void incrementScore(UUID productId, int quantity) {
        LocalDate today = LocalDate.now();
        String productIdStr = productId.toString();

        // 일별 랭킹 업데이트
        String dailyKey = DAILY_PREFIX + today.format(DATE_FORMAT);
        RScoredSortedSet<String> dailySet = redissonClient.getScoredSortedSet(dailyKey);
        dailySet.addScore(productIdStr, quantity);
        dailySet.expire(Duration.ofDays(3));

        // 주별 랭킹 업데이트
        int weekOfYear = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.getYear();
        String weeklyKey = WEEKLY_PREFIX + year + ":" + String.format("%02d", weekOfYear);
        RScoredSortedSet<String> weeklySet = redissonClient.getScoredSortedSet(weeklyKey);
        weeklySet.addScore(productIdStr, quantity);
        weeklySet.expire(Duration.ofDays(10));
    }

    /**
     * 일별 인기 상품 조회 (TOP N)
     */
    public List<RankingEntry> getDailyRanking(int limit) {
        return getDailyRanking(LocalDate.now(), limit);
    }

    /**
     * 특정 날짜 일별 인기 상품 조회
     */
    public List<RankingEntry> getDailyRanking(LocalDate date, int limit) {
        String key = DAILY_PREFIX + date.format(DATE_FORMAT);
        return getRanking(key, limit);
    }

    /**
     * 주별 인기 상품 조회 (TOP N)
     */
    public List<RankingEntry> getWeeklyRanking(int limit) {
        LocalDate today = LocalDate.now();
        int weekOfYear = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.getYear();
        String key = WEEKLY_PREFIX + year + ":" + String.format("%02d", weekOfYear);
        return getRanking(key, limit);
    }

    private List<RankingEntry> getRanking(String key, int limit) {
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        Collection<ScoredEntry<String>> entries = set.entryRangeReversed(0, limit - 1);
        
        List<RankingEntry> result = new ArrayList<>();
        int rank = 1;
        for (ScoredEntry<String> entry : entries) {
            result.add(new RankingEntry(
                    rank++,
                    UUID.fromString(entry.getValue()),
                    entry.getScore().longValue()
            ));
        }
        return result;
    }

    /**
     * 랭킹 엔트리
     */
    public record RankingEntry(int rank, UUID productId, long score) {}
}
