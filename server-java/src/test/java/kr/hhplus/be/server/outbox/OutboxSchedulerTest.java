package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.AbstractIntegrationTest;
import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.infrastructure.outbox.message.MockMessageProducer;
import kr.hhplus.be.server.infrastructure.outbox.scheduler.OutboxScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxScheduler 발행 테스트
 * 
 * 검증 항목:
 * 1. PENDING 상태의 Outbox 이벤트가 발행됨
 * 2. 발행 후 PUBLISHED 상태로 변경됨
 * 3. MockMessageProducer에 메시지가 전달됨
 * 4. 재시도 메커니즘 동작 확인
 */
class OutboxSchedulerTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

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
        mockMessageProducer.clear();
    }

    @Test
    @DisplayName("OutboxScheduler가 PENDING 이벤트를 발행한다")
    void publishPendingEvents_Success() {
        // Given
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(productId, 2)
        );
        Order order = orderService.createOrder(userId, items);

        // Outbox 이벤트 확인
        List<Outbox> pendingOutboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);
        assertThat(pendingOutboxes).hasSize(1);

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        // 1. MockMessageProducer에 메시지 전달됨
        List<MockMessageProducer.Message> sentMessages = mockMessageProducer.getSentMessages();
        assertThat(sentMessages).hasSize(1);
        assertThat(sentMessages.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(sentMessages.get(0).getPayload()).contains(order.getId().toString());

        // 2. Outbox 상태가 PUBLISHED로 변경됨
        List<Outbox> publishedOutboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PUBLISHED, 3);
        assertThat(publishedOutboxes).hasSize(1);
        assertThat(publishedOutboxes.get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publishedOutboxes.get(0).getPublishedAt()).isNotNull();

        // 3. PENDING 상태의 Outbox가 없음
        List<Outbox> remainingPending = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);
        assertThat(remainingPending).isEmpty();
    }

    @Test
    @DisplayName("여러 개의 PENDING 이벤트를 모두 발행한다")
    void publishPendingEvents_Multiple() {
        // Given
        // 첫 번째 주문
        orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 1)
        ));

        // 두 번째 주문
        orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // PENDING 이벤트 2개 확인
        List<Outbox> pendingOutboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);
        assertThat(pendingOutboxes).hasSize(2);

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        // 1. MockMessageProducer에 2개 메시지 전달됨
        List<MockMessageProducer.Message> sentMessages = mockMessageProducer.getSentMessages();
        assertThat(sentMessages).hasSize(2);
        assertThat(sentMessages).allMatch(msg -> msg.getEventType().equals("ORDER_CREATED"));

        // 2. 모든 Outbox가 PUBLISHED 상태
        List<Outbox> publishedOutboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PUBLISHED, 3);
        assertThat(publishedOutboxes).hasSize(2);

        // 3. PENDING 상태의 Outbox가 없음
        List<Outbox> remainingPending = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);
        assertThat(remainingPending).isEmpty();
    }

    @Test
    @DisplayName("이미 PUBLISHED된 이벤트는 재발행하지 않는다")
    void publishPendingEvents_SkipPublished() {
        // Given
        orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 1)
        ));

        // 첫 번째 발행
        outboxScheduler.publishPendingEvents();
        assertThat(mockMessageProducer.getSentMessages()).hasSize(1);

        // MockMessageProducer 초기화
        mockMessageProducer.clear();

        // When
        // 두 번째 발행 시도
        outboxScheduler.publishPendingEvents();

        // Then
        // 재발행되지 않음
        assertThat(mockMessageProducer.getSentMessages()).isEmpty();
    }

    @Test
    @DisplayName("Outbox payload가 올바른 JSON 형식이다")
    void publishPendingEvents_ValidJsonPayload() {
        // Given
        Order order = orderService.createOrder(userId, List.of(
                new OrderItemRequest(productId, 2)
        ));

        // When
        outboxScheduler.publishPendingEvents();

        // Then
        List<MockMessageProducer.Message> sentMessages = mockMessageProducer.getSentMessages();
        String payload = sentMessages.get(0).getPayload();

        // JSON 형식 검증
        assertThat(payload).startsWith("{");
        assertThat(payload).endsWith("}");
        assertThat(payload).contains("orderId");
        assertThat(payload).contains("userId");
        assertThat(payload).contains("totalAmount");
        assertThat(payload).contains("status");
    }
}
