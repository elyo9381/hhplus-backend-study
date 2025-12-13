package kr.hhplus.be.server.payment.domain;

import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentRepository;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.infrastructure.payment.persistence.PaymentRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PaymentRepositoryImpl.class)
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldSaveAndFindPayment() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);

        // when
        Payment saved = paymentRepository.save(payment);
        Optional<Payment> found = paymentRepository.findById(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getOrderId()).isEqualTo(orderId);
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getAmount()).isEqualTo(50000L);
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldFindPaymentByOrderId() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);
        paymentRepository.save(payment);

        // when
        Optional<Payment> found = paymentRepository.findByOrderId(orderId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(orderId);
    }

    @Test
    void shouldReturnEmptyWhenPaymentNotFound() {
        // given
        UUID nonExistentId = UUID.randomUUID();

        // when
        Optional<Payment> found = paymentRepository.findById(nonExistentId);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenOrderIdNotFound() {
        // given
        UUID nonExistentOrderId = UUID.randomUUID();

        // when
        Optional<Payment> found = paymentRepository.findByOrderId(nonExistentOrderId);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdatePaymentStatus() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, 50000L, 50000L);
        Payment saved = paymentRepository.save(payment);

        // when
        saved.complete();
        Payment updated = paymentRepository.save(saved);

        // then
        Optional<Payment> found = paymentRepository.findById(updated.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }
}
