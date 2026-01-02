package kr.hhplus.be.server.point;

import kr.hhplus.be.server.infrastructure.point.persistence.PointHistoryEntity;
import kr.hhplus.be.server.infrastructure.point.persistence.PointHistoryType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PointHistoryEntityTest {

    @Test
    void shouldCreateChargeHistory() {
        // given
        UUID userId = UUID.randomUUID();
        Long amount = 10000L;
        Long balance = 10000L;
        String description = "포인트 충전";

        // when
        PointHistoryEntity history = new PointHistoryEntity(
                userId, PointHistoryType.CHARGE, amount, balance, description
        );

        // then
        assertThat(history.getId()).isNotNull();
        assertThat(history.getUserId()).isEqualTo(userId);
        assertThat(history.getType()).isEqualTo(PointHistoryType.CHARGE);
        assertThat(history.getAmount()).isEqualTo(amount);
        assertThat(history.getBalance()).isEqualTo(balance);
        assertThat(history.getDescription()).isEqualTo(description);
        assertThat(history.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldCreateUseHistory() {
        // given
        UUID userId = UUID.randomUUID();
        Long amount = 3000L;
        Long balance = 7000L;

        // when
        PointHistoryEntity history = new PointHistoryEntity(
                userId, PointHistoryType.USE, amount, balance, "주문 결제"
        );

        // then
        assertThat(history.getType()).isEqualTo(PointHistoryType.USE);
        assertThat(history.getAmount()).isEqualTo(amount);
        assertThat(history.getBalance()).isEqualTo(balance);
    }

    @Test
    void shouldCreateHistoryWithoutDescription() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        PointHistoryEntity history = new PointHistoryEntity(
                userId, PointHistoryType.CHARGE, 10000L, 10000L, null
        );

        // then
        assertThat(history.getDescription()).isNull();
    }
}
