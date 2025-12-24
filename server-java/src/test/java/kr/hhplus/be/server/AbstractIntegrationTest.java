package kr.hhplus.be.server;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 테스트를 위한 Base 클래스
 * 
 * 특징:
 * - Singleton Container 패턴으로 모든 테스트가 하나의 MySQL 컨테이너 공유
 * - @Transactional로 테스트 간 데이터 격리 (각 테스트 후 롤백)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest extends TestContainerSupport {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
    }

    @BeforeEach
    void setUp() {
        // 각 테스트 전 실행할 공통 로직
    }
}
