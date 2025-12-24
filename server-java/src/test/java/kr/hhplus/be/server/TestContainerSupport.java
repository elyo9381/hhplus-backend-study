package kr.hhplus.be.server;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

/**
 * Singleton Container 패턴
 * 
 * 모든 테스트에서 하나의 MySQL 컨테이너를 공유하여 테스트 속도 향상
 * 컨테이너는 JVM 종료 시 자동으로 정리됨
 */
public abstract class TestContainerSupport {

    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL_CONTAINER;

    static {
        MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0.40")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);  // 컨테이너 재사용
        MYSQL_CONTAINER.start();
    }
}
