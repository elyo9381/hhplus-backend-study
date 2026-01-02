package kr.hhplus.be.server.point;

import kr.hhplus.be.server.infrastructure.point.persistence.PointEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointEntityTest {

    @Test
    void shouldCreatePoint() {
        // given
        UUID userId = UUID.randomUUID();
        Long amount = 10000L;
        LocalDateTime expiredAt = LocalDateTime.now().plusYears(1);

        // when
        PointEntity point = new PointEntity(userId, amount, expiredAt);

        // then
        assertThat(point.getId()).isNotNull();
        assertThat(point.getUserId()).isEqualTo(userId);
        assertThat(point.getAmount()).isEqualTo(amount);
        assertThat(point.getExpiredAt()).isEqualTo(expiredAt);
        assertThat(point.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldNotBeExpired() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDateTime futureDate = LocalDateTime.now().plusDays(1);
        PointEntity point = new PointEntity(userId, 10000L, futureDate);

        // when & then
        assertThat(point.isExpired()).isFalse();
    }

    @Test
    void shouldBeExpired() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        PointEntity point = new PointEntity(userId, 10000L, pastDate);

        // when & then
        assertThat(point.isExpired()).isTrue();
    }

    @Test
    void shouldUsePoint() {
        // given
        UUID userId = UUID.randomUUID();
        PointEntity point = new PointEntity(userId, 10000L, LocalDateTime.now().plusYears(1));

        // when
        point.use(3000L);

        // then
        assertThat(point.getAmount()).isEqualTo(7000L);
    }

    @Test
    void shouldThrowExceptionWhenInsufficientPoint() {
        // given
        UUID userId = UUID.randomUUID();
        PointEntity point = new PointEntity(userId, 5000L, LocalDateTime.now().plusYears(1));

        // when & then
        assertThatThrownBy(() -> point.use(10000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient point");
    }
}
