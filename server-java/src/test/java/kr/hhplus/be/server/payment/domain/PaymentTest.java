package kr.hhplus.be.server.payment.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void shouldCreatePaymentWithPendingStatus() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Long amount = 50000L;
        Long pointAmount = 50000L;

        // when
        Payment payment = new Payment(orderId, userId, amount, pointAmount);

        // then
        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getUserId()).isEqualTo(userId);
        assertThat(payment.getPaymentType()).isEqualTo(PaymentType.POINT);
        assertThat(payment.getAmount()).isEqualTo(50000L);
        assertThat(payment.getPointAmount()).isEqualTo(50000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldCompletePayment() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);

        // when
        payment.complete();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void shouldFailPayment() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);

        // when
        payment.fail();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldThrowExceptionWhenCompleteNonPendingPayment() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);
        payment.complete();

        // when & then
        assertThatThrownBy(payment::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment is not pending");
    }

    @Test
    void shouldThrowExceptionWhenFailNonPendingPayment() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);
        payment.fail();

        // when & then
        assertThatThrownBy(payment::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment is not pending");
    }

    @Test
    void shouldNotChangeStatusFromSuccessToFailed() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);
        payment.complete();

        // when & then
        assertThatThrownBy(payment::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment is not pending");
    }

    @Test
    void shouldNotChangeStatusFromFailedToSuccess() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);
        payment.fail();

        // when & then
        assertThatThrownBy(payment::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment is not pending");
    }
}
