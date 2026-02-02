package kr.hhplus.be.server;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 Testcontainers 지원
 *
 * Lazy initialization으로 실제 사용 시에만 컨테이너 시작
 */
public abstract class TestContainerSupport {

    private static MySQLContainer<?> mysqlContainer;
    private static KafkaContainer kafkaContainer;
    private static GenericContainer<?> redisContainer;

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

    protected static synchronized KafkaContainer getKafkaContainer() {
        if (kafkaContainer == null) {
            kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withReuse(true);
            kafkaContainer.start();
        }
        return kafkaContainer;
    }

    protected static String getBootstrapServers() {
        return getKafkaContainer().getBootstrapServers();
    }

    protected static synchronized GenericContainer<?> getRedisContainer() {
        if (redisContainer == null) {
            redisContainer = new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withReuse(true);
            redisContainer.start();
        }
        return redisContainer;
    }

    protected static String getRedisHost() {
        return getRedisContainer().getHost();
    }

    protected static Integer getRedisPort() {
        return getRedisContainer().getFirstMappedPort();
    }
}
