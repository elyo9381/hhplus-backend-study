package kr.hhplus.be.server.payment.domain;

import kr.hhplus.be.server.payment.infrastructure.persistence.PaymentEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEntityTest {

    @Test
    void shouldConvertToDomain() {
        // given
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        PaymentEntity entity = new PaymentEntity(
                id, orderId, userId, PaymentType.POINT,
                50000L, 50000L, PaymentStatus.PENDING,
                now, now
        );

        // when
        Payment domain = entity.toDomain();

        // then
        assertThat(domain.getId()).isEqualTo(id);
        assertThat(domain.getOrderId()).isEqualTo(orderId);
        assertThat(domain.getUserId()).isEqualTo(userId);
        assertThat(domain.getPaymentType()).isEqualTo(PaymentType.POINT);
        assertThat(domain.getAmount()).isEqualTo(50000L);
        assertThat(domain.getPointAmount()).isEqualTo(50000L);
        assertThat(domain.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(domain.getCreatedAt()).isEqualTo(now);
        assertThat(domain.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void shouldConvertFromDomain() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment domain = new Payment(orderId, userId, 50000L, 50000L);

        // when
        PaymentEntity entity = PaymentEntity.fromDomain(domain);

        // then
        assertThat(entity.getId()).isEqualTo(domain.getId());
        assertThat(entity.getOrderId()).isEqualTo(orderId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getPaymentType()).isEqualTo(PaymentType.POINT);
        assertThat(entity.getAmount()).isEqualTo(50000L);
        assertThat(entity.getPointAmount()).isEqualTo(50000L);
        assertThat(entity.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldMaintainConsistencyInBidirectionalConversion() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment originalDomain = new Payment(orderId, userId, 50000L, 50000L);

        // when
        PaymentEntity entity = PaymentEntity.fromDomain(originalDomain);
        Payment convertedDomain = entity.toDomain();

        // then
        assertThat(convertedDomain.getId()).isEqualTo(originalDomain.getId());
        assertThat(convertedDomain.getOrderId()).isEqualTo(originalDomain.getOrderId());
        assertThat(convertedDomain.getUserId()).isEqualTo(originalDomain.getUserId());
        assertThat(convertedDomain.getAmount()).isEqualTo(originalDomain.getAmount());
        assertThat(convertedDomain.getStatus()).isEqualTo(originalDomain.getStatus());
    }
}
