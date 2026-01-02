package kr.hhplus.be.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleSpringBootTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
