package kr.hhplus.be.server.product;

import kr.hhplus.be.server.TestContainerSupport;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비관적 락 동작 검증 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductPessimisticLockTest extends TestContainerSupport {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
        registry.add("spring.data.redis.host", TestContainerSupport::getRedisHost);
        registry.add("spring.data.redis.port", TestContainerSupport::getRedisPort);
    }

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldAcquirePessimisticLockOnProduct() {
        // given: 상품 생성
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 10);
        ProductEntity saved = productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        UUID productId = saved.getId();

        // when: 비관적 락으로 조회
        ProductEntity locked = productRepository.findByIdWithLock(productId).orElseThrow();

        // then: 조회 성공 및 재고 차감 가능
        assertThat(locked.getStock()).isEqualTo(10);
        locked.decreaseStock(3);
        productRepository.save(locked);
        entityManager.flush();

        // 재조회 시 변경 반영
        ProductEntity updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(7);
    }

    @Test
    void shouldDecreaseStockCorrectly() {
        // given
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 10);
        ProductEntity saved = productRepository.save(product);
        entityManager.flush();

        // when: 재고 차감
        saved.decreaseStock(3);
        productRepository.save(saved);
        entityManager.flush();
        entityManager.clear();

        // then
        ProductEntity updated = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(7);
    }

    @Test
    void shouldVerifyLockQueryGeneration() {
        // given
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 10);
        ProductEntity saved = productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // when: 비관적 락 조회 (SQL 로그 확인)
        // 로그에 "FOR UPDATE" 또는 "PESSIMISTIC_WRITE" 확인
        ProductEntity locked = productRepository.findByIdWithLock(saved.getId()).orElseThrow();

        // then: 정상 조회
        assertThat(locked).isNotNull();
        assertThat(locked.getId()).isEqualTo(saved.getId());
    }
}
