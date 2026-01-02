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
 * Outbox 통합 테스트
 * 
 * 전체 플로우 검증:
 * - 주문 생성 → Outbox 저장
 * - 결제 완료 → Outbox 저장
 * - 스케줄러 발행 → 상태 변경
 */
class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxScheduler outboxScheduler;

    @Autowired
    private OutboxTestHelper testHelper;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        userId = UUID.randomUUID();
        productId = testHelper.createTestProduct("테스트 상품", BigDecimal.valueOf(10000), 100);
        testHelper.createTestPoint(userId, 100000L);
    }

    @Test
    @DisplayName("주문 생성 시 ORDER_CREATED Outbox가 저장된다")
    void createOrder_OutboxSaved() {
        // When
        Order order = orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // Then
        List<Outbox> outboxes = outboxRepository.findByStatus(OutboxStatus.PENDING);
        assertThat(outboxes).hasSize(1);
        assertThat(outboxes.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(outboxes.get(0).getAggregateId()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("결제 완료 시 PAYMENT_COMPLETED Outbox가 저장된다")
    void executePayment_OutboxSaved() {
        // Given
        Order order = orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // When
        Payment payment = paymentService.executePayment(order.getId(), userId, UUID.randomUUID().toString());

        // Then
        List<Outbox> outboxes = outboxRepository.findByStatus(OutboxStatus.PENDING);
        assertThat(outboxes).hasSize(2);
        assertThat(outboxes).anyMatch(o -> o.getEventType().equals("ORDER_CREATED"));
        assertThat(outboxes).anyMatch(o -> o.getEventType().equals("PAYMENT_COMPLETED"));
    }

    @Test
    @DisplayName("스케줄러 발행 후 Outbox 상태가 PUBLISHED로 변경된다")
    void scheduler_StatusPublished() {
        // Given
        orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        List<Outbox> pending = outboxRepository.findByStatus(OutboxStatus.PENDING);
        List<Outbox> published = outboxRepository.findByStatus(OutboxStatus.PUBLISHED);

        assertThat(pending).isEmpty();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("전체 플로우: 주문 → 결제 → 발행")
    void fullFlow() {
        // Given: 주문 생성
        Order order = orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // When: 결제 실행
        paymentService.executePayment(order.getId(), userId, UUID.randomUUID().toString());

        // Then: 발행
        outboxScheduler.publishPendingEvents();

        List<Outbox> published = outboxRepository.findByStatus(OutboxStatus.PUBLISHED);
        assertThat(published).hasSize(2);
    }
}
