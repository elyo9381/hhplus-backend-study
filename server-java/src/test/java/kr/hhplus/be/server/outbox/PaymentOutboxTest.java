package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.AbstractIntegrationTest;
import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.infrastructure.outbox.message.MockMessageProducer;
import kr.hhplus.be.server.presentation.scheduler.OutboxScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment Outbox 테스트
 * 
 * 검증 항목:
 * 1. 결제 완료 시 PAYMENT_COMPLETED 이벤트 발행
 * 2. Order 상태 변경과 Outbox 저장이 같은 트랜잭션
 * 3. 전체 플로우 (주문 → 결제 → Outbox 발행)
 */
class PaymentOutboxTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxScheduler outboxScheduler;

    @Autowired
    private MockMessageProducer mockMessageProducer;

    @Autowired
    private OutboxTestHelper testHelper;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = testHelper.createTestProduct("테스트 상품", BigDecimal.valueOf(10000), 100);
        testHelper.createTestPoint(userId, 100000L);
        mockMessageProducer.clear();
    }

    @Test
    @DisplayName("결제 완료 시 PAYMENT_COMPLETED Outbox 이벤트가 생성된다")
    void executePayment_Success_OutboxCreated() {
        // Given
        Order order = orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // When
        Payment payment = paymentService.executePayment(order.getId(), userId);

        // Then
        assertThat(payment.getStatus().name()).isEqualTo("SUCCESS");

        // Outbox 이벤트 확인 (ORDER_CREATED + PAYMENT_COMPLETED)
        List<Outbox> outboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        assertThat(outboxes).hasSize(2);
        assertThat(outboxes).anyMatch(o -> o.getEventType().equals("ORDER_CREATED"));
        assertThat(outboxes).anyMatch(o -> o.getEventType().equals("PAYMENT_COMPLETED"));

        // PAYMENT_COMPLETED 이벤트 상세 확인
        Outbox paymentOutbox = outboxes.stream()
                .filter(o -> o.getEventType().equals("PAYMENT_COMPLETED"))
                .findFirst()
                .orElseThrow();

        assertThat(paymentOutbox.getAggregateId()).isEqualTo(payment.getId());
        assertThat(paymentOutbox.getPayload()).contains(payment.getId().toString());
        assertThat(paymentOutbox.getPayload()).contains(order.getId().toString());
        assertThat(paymentOutbox.getPayload()).contains("PAID"); // orderStatus
    }

    @Test
    @DisplayName("전체 플로우: 주문 생성 → 결제 → Outbox 발행")
    void fullFlow_OrderToPaymentToOutbox() {
        // Given: 주문 생성
        Order order = orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // When: 결제 실행
        Payment payment = paymentService.executePayment(order.getId(), userId);

        // Then: Outbox 발행
        outboxScheduler.publishPendingEvents();

        // 1. MockMessageProducer에 2개 메시지 전달됨
        List<MockMessageProducer.Message> sentMessages = mockMessageProducer.getSentMessages();
        assertThat(sentMessages).hasSize(2);

        // 2. ORDER_CREATED 메시지 확인
        MockMessageProducer.Message orderMessage = sentMessages.stream()
                .filter(msg -> msg.getEventType().equals("ORDER_CREATED"))
                .findFirst()
                .orElseThrow();
        assertThat(orderMessage.getPayload()).contains(order.getId().toString());
        assertThat(orderMessage.getPayload()).contains("PENDING"); // 주문 생성 시점 상태

        // 3. PAYMENT_COMPLETED 메시지 확인
        MockMessageProducer.Message paymentMessage = sentMessages.stream()
                .filter(msg -> msg.getEventType().equals("PAYMENT_COMPLETED"))
                .findFirst()
                .orElseThrow();
        assertThat(paymentMessage.getPayload()).contains(payment.getId().toString());
        assertThat(paymentMessage.getPayload()).contains("PAID"); // 결제 완료 후 상태

        // 4. 모든 Outbox가 PUBLISHED 상태
        List<Outbox> publishedOutboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PUBLISHED, 3);
        assertThat(publishedOutboxes).hasSize(2);
    }

    @Test
    @DisplayName("결제 실패 시 PAYMENT_COMPLETED Outbox가 생성되지 않는다")
    void executePayment_Fail_NoOutbox() {
        // Given
        Order order = orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        UUID wrongUserId = UUID.randomUUID(); // 잘못된 사용자

        // When & Then
        try {
            paymentService.executePayment(order.getId(), wrongUserId);
        } catch (IllegalArgumentException e) {
            // 예외 발생 예상
        }

        // Outbox 이벤트 확인 (ORDER_CREATED만 있어야 함)
        List<Outbox> outboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        assertThat(outboxes).hasSize(1);
        assertThat(outboxes.get(0).getEventType()).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED payload에 orderStatus가 PAID로 포함된다")
    void executePayment_OutboxPayloadContainsPaidStatus() {
        // Given
        Order order = orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // When
        paymentService.executePayment(order.getId(), userId);

        // Then
        List<Outbox> outboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        Outbox paymentOutbox = outboxes.stream()
                .filter(o -> o.getEventType().equals("PAYMENT_COMPLETED"))
                .findFirst()
                .orElseThrow();

        String payload = paymentOutbox.getPayload();
        assertThat(payload).contains("orderStatus");
        assertThat(payload).contains("PAID");
    }
}
