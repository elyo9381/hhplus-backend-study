package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.AbstractIntegrationTest;
import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox 패턴 트랜잭션 정합성 테스트
 * 
 * 검증 항목:
 * 1. Order 저장 성공 시 Outbox도 저장됨
 * 2. Order 저장 실패 시 Outbox도 롤백됨
 * 3. Outbox와 Order가 같은 트랜잭션에서 커밋됨
 */
class OutboxTransactionTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxTestHelper testHelper;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = testHelper.createTestProduct("테스트 상품", BigDecimal.valueOf(10000), 100);
    }

    @Test
    @DisplayName("주문 생성 성공 시 Outbox 이벤트도 함께 저장된다")
    void createOrder_Success_OutboxSaved() {
        // Given
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(productId, 2)
        );

        // When
        Order order = orderService.createOrder(userId, items);

        // Then
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus().name()).isEqualTo("PENDING");

        // Outbox 이벤트 확인
        List<Outbox> outboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        assertThat(outboxes).hasSize(1);
        assertThat(outboxes.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(outboxes.get(0).getAggregateId()).isEqualTo(order.getId());
        assertThat(outboxes.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxes.get(0).getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("주문 생성 실패 시 Outbox 이벤트도 롤백된다")
    void createOrder_Fail_OutboxRollback() {
        // Given
        UUID invalidProductId = UUID.randomUUID(); // 존재하지 않는 상품
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(invalidProductId, 1)
        );

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(userId, items))
                .isInstanceOf(IllegalArgumentException.class);

        // Outbox 이벤트가 저장되지 않았는지 확인
        List<Outbox> outboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        assertThat(outboxes).isEmpty();
    }

    @Test
    @DisplayName("재고 부족 시 Order와 Outbox 모두 롤백된다")
    void createOrder_InsufficientStock_AllRollback() {
        // Given
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(productId, 200) // 재고(100)보다 많은 수량
        );

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(userId, items))
                .isInstanceOf(IllegalArgumentException.class);

        // Outbox 이벤트가 저장되지 않았는지 확인
        List<Outbox> outboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        assertThat(outboxes).isEmpty();
    }

    @Test
    @DisplayName("Outbox payload에 주문 정보가 포함된다")
    void createOrder_OutboxPayloadContainsOrderInfo() {
        // Given
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(productId, 2)
        );

        // When
        Order order = orderService.createOrder(userId, items);

        // Then
        List<Outbox> outboxes = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        Outbox outbox = outboxes.get(0);
        String payload = outbox.getPayload();

        assertThat(payload).contains(order.getId().toString());
        assertThat(payload).contains(userId.toString());
        assertThat(payload).contains("PENDING");
        assertThat(payload).contains("20000"); // totalAmount
    }
}
