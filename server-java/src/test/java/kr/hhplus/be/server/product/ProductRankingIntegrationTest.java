package kr.hhplus.be.server.product;

import kr.hhplus.be.server.TestContainerSupport;
import kr.hhplus.be.server.infrastructure.product.ProductRankingRepository;
import kr.hhplus.be.server.infrastructure.product.ProductRankingRepository.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인기상품 랭킹 Redis 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProductRankingIntegrationTest extends TestContainerSupport {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private ProductRankingRepository productRankingRepository;

    private UUID productA;
    private UUID productB;
    private UUID productC;

    @BeforeEach
    void setUp() {
        productA = UUID.randomUUID();
        productB = UUID.randomUUID();
        productC = UUID.randomUUID();
    }

    @Test
    @DisplayName("상품 주문 수량 증가 및 일별 랭킹 조회")
    void 일별_랭킹_조회() {
        // given
        productRankingRepository.incrementScore(productA, 10);
        productRankingRepository.incrementScore(productB, 30);
        productRankingRepository.incrementScore(productC, 20);

        // when
        List<RankingEntry> ranking = productRankingRepository.getDailyRanking(3);

        // then
        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0).productId()).isEqualTo(productB); // 30
        assertThat(ranking.get(0).score()).isEqualTo(30);
        assertThat(ranking.get(0).rank()).isEqualTo(1);
        
        assertThat(ranking.get(1).productId()).isEqualTo(productC); // 20
        assertThat(ranking.get(2).productId()).isEqualTo(productA); // 10
    }

    @Test
    @DisplayName("동일 상품 누적 주문 수량 반영")
    void 누적_주문_수량() {
        // given
        UUID product = UUID.randomUUID();
        productRankingRepository.incrementScore(product, 5);
        productRankingRepository.incrementScore(product, 3);
        productRankingRepository.incrementScore(product, 2);

        // when
        List<RankingEntry> ranking = productRankingRepository.getDailyRanking(10);

        // then
        RankingEntry entry = ranking.stream()
                .filter(e -> e.productId().equals(product))
                .findFirst()
                .orElseThrow();
        assertThat(entry.score()).isEqualTo(10); // 5 + 3 + 2
    }

    @Test
    @DisplayName("주별 랭킹 조회")
    void 주별_랭킹_조회() {
        // given
        productRankingRepository.incrementScore(productA, 100);
        productRankingRepository.incrementScore(productB, 50);

        // when
        List<RankingEntry> ranking = productRankingRepository.getWeeklyRanking(2);

        // then
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).productId()).isEqualTo(productA);
        assertThat(ranking.get(0).score()).isEqualTo(100);
    }

    @Test
    @DisplayName("랭킹 limit 제한")
    void 랭킹_limit_제한() {
        // given
        for (int i = 0; i < 20; i++) {
            productRankingRepository.incrementScore(UUID.randomUUID(), i + 1);
        }

        // when
        List<RankingEntry> top5 = productRankingRepository.getDailyRanking(5);

        // then
        assertThat(top5).hasSize(5);
        assertThat(top5.get(0).score()).isEqualTo(20); // 가장 높은 점수
    }
}
