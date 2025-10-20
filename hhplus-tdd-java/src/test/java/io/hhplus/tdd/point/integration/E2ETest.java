package io.hhplus.tdd.point.integration;

import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * E2E 테스트를 통해서 실제 비즈니스 흐름 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class E2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    void e2e_Test() {
        long userId = 1L;

        // 충전
        HttpEntity<Long> chargeRequest = new HttpEntity<>(1000L);
        ResponseEntity<UserPoint> chargeResponse = restTemplate.exchange(
                "/point/{id}/charge", HttpMethod.PATCH, chargeRequest, UserPoint.class, userId
        );
        assertThat(chargeResponse.getBody().point()).isEqualTo(1000L);

        // 조회
        UserPoint point = restTemplate.getForObject("/point/{id}", UserPoint.class, userId);
        assertThat(point.point()).isEqualTo(1000L);

        // 사용
        HttpEntity<Long> useRequest = new HttpEntity<>(500L);
        ResponseEntity<UserPoint> useResponse = restTemplate.exchange(
                "/point/{id}/use", HttpMethod.PATCH, useRequest, UserPoint.class, userId
        );
        assertThat(useResponse.getBody().point()).isEqualTo(500L);

        // 히스토리 조회
        PointHistory[] histories = restTemplate.getForObject("/point/{id}/histories", PointHistory[].class, userId);

        assertThat(histories.length).isEqualTo(2);
        assertThat(histories[0].type()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories[1].type()).isEqualTo(TransactionType.USE);
    }
}