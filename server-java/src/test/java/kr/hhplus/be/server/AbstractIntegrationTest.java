package kr.hhplus.be.server;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 통합 테스트를 위한 Base 클래스
 * 
 * 주의: @Transactional은 개별 테스트에서 필요시 적용
 * (동시성/스케줄러 테스트에서는 사용하면 안됨)
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest extends TestContainerSupport {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
    }
}
