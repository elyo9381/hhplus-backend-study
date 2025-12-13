package kr.hhplus.be.server;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트를 위한 Base 클래스
 * 
 * 특징:
 * - Testcontainers MySQL 8.0.40 사용
 * - Singleton 패턴으로 모든 테스트가 하나의 컨테이너 공유 (성능 최적화)
 * - @ServiceConnection으로 Spring Boot가 자동으로 DataSource 설정
 * - @Transactional로 테스트 간 데이터 격리 (각 테스트 후 롤백)
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    /**
     * MySQL 8.0.40 컨테이너
     * - static: 모든 테스트가 하나의 컨테이너 공유
     * - @Container: Testcontainers가 생명주기 관리
     * - @ServiceConnection: Spring Boot가 자동으로 DataSource 설정
     */
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--default-time-zone=+00:00"
            );

    @BeforeEach
    void setUp() {
        // 각 테스트 전 실행할 공통 로직
        // @Transactional로 인해 각 테스트 후 자동 롤백됨
    }
}
