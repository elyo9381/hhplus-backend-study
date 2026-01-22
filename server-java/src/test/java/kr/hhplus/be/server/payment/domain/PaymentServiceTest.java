package kr.hhplus.be.server.payment.domain;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderItem;
import kr.hhplus.be.server.domain.order.OrderRepository;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentRepository;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.application.payment.PointPort;
import kr.hhplus.be.server.infrastructure.product.ProductRankingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PointPort pointPort;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ProductRankingRepository productRankingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, 
                orderRepository, 
                pointPort, 
                outboxRepository,
                new ObjectMapper(),
                productRankingRepository,
                eventPublisher
        );
    }

    @Test
    void shouldExecutePaymentSuccessfully() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        OrderItem orderItem = new OrderItem(productId, "Product A", 10000L, 2);
        Order order = new Order(userId, List.of(orderItem));

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithLock(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        
        Payment savedPayment = new Payment(orderId, userId, idempotencyKey, 20000L, 20000L);
        savedPayment.complete();
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // when
        Payment result = paymentService.executePayment(orderId, userId, idempotencyKey);

        // then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getAmount()).isEqualTo(20000L);
        verify(pointPort).usePoint(userId, 20000L);
        verify(paymentRepository).save(any(Payment.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldReturnExistingPaymentWhenIdempotencyKeyExists() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        Payment existingPayment = new Payment(orderId, userId, idempotencyKey, 20000L, 20000L);
        existingPayment.complete();
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        // when
        Payment result = paymentService.executePayment(orderId, userId, idempotencyKey);

        // then
        assertThat(result).isEqualTo(existingPayment);
        verify(orderRepository, never()).findByIdWithLock(any());
        verify(pointPort, never()).usePoint(any(), any());
    }

    @Test
    void shouldThrowExceptionWhenPaymentAlreadyExistsForOrder() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        OrderItem orderItem = new OrderItem(productId, "Product A", 10000L, 2);
        Order order = new Order(userId, List.of(orderItem));

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithLock(orderId)).thenReturn(Optional.of(order));
        Payment existingPayment = new Payment(orderId, userId, "other-key", 20000L, 20000L);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingPayment));

        // when & then
        assertThatThrownBy(() -> paymentService.executePayment(orderId, userId, idempotencyKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment already exists for this order");

        verify(pointPort, never()).usePoint(any(), any());
    }

    @Test
    void shouldThrowExceptionWhenOrderNotFound() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithLock(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.executePayment(orderId, userId, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order not found");

        verify(pointPort, never()).usePoint(any(), any());
    }

    @Test
    void shouldThrowExceptionWhenUserMismatch() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        OrderItem orderItem = new OrderItem(productId, "Product A", 10000L, 2);
        Order order = new Order(differentUserId, List.of(orderItem));

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithLock(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.executePayment(orderId, userId, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User mismatch");

        verify(pointPort, never()).usePoint(any(), any());
    }

    @Test
    void shouldThrowExceptionWhenOrderIsNotPending() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        OrderItem orderItem = new OrderItem(productId, "Product A", 10000L, 2);
        Order order = new Order(userId, List.of(orderItem));
        order.completePayment(20000L); // PAID 상태로 변경

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithLock(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.executePayment(orderId, userId, idempotencyKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order is not pending");

        verify(pointPort, never()).usePoint(any(), any());
    }

    @Test
    void shouldThrowExceptionWhenInsufficientPoints() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        OrderItem orderItem = new OrderItem(productId, "Product A", 10000L, 2);
        Order order = new Order(userId, List.of(orderItem));

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithLock(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException("Insufficient point balance"))
                .when(pointPort).usePoint(userId, 20000L);

        // when & then
        assertThatThrownBy(() -> paymentService.executePayment(orderId, userId, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient point balance");

        verify(paymentRepository, never()).save(any());
    }
}
