package kr.hhplus.be.server;

import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트용 Testcontainers 지원
 * 
 * Lazy initialization으로 실제 사용 시에만 컨테이너 시작
 */
public abstract class TestContainerSupport {

    private static MySQLContainer<?> mysqlContainer;

    protected static synchronized MySQLContainer<?> getMySQLContainer() {
        if (mysqlContainer == null) {
            mysqlContainer = new MySQLContainer<>("mysql:8.0.40")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);
            mysqlContainer.start();
        }
        return mysqlContainer;
    }

    protected static String getJdbcUrl() {
        return getMySQLContainer().getJdbcUrl();
    }

    protected static String getUsername() {
        return getMySQLContainer().getUsername();
    }

    protected static String getPassword() {
        return getMySQLContainer().getPassword();
    }
}
