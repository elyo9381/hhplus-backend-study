package kr.hhplus.be.server.order.domain;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderItem;
import kr.hhplus.be.server.domain.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void shouldCreateOrder() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 2),
                new OrderItem(UUID.randomUUID(), "Product B", 20000L, 1)
        );

        // when
        Order order = new Order(userId, items);

        // then
        assertThat(order.getId()).isNotNull();
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualTo(40000L); // 20000 + 20000
        assertThat(order.getDiscountAmount()).isEqualTo(0L);
        assertThat(order.getFinalAmount()).isEqualTo(40000L);
        assertThat(order.getPaidAmount()).isEqualTo(0L);
        assertThat(order.getPointAmount()).isEqualTo(0L);
        assertThat(order.getRemainingAmount()).isEqualTo(40000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldCompletePayment() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 2)
        );
        Order order = new Order(userId, items);
        Long pointAmount = 5000L;

        // when
        order.completePayment(pointAmount);

        // then
        assertThat(order.getPointAmount()).isEqualTo(5000L);
        assertThat(order.getPaidAmount()).isEqualTo(20000L);
        assertThat(order.getRemainingAmount()).isEqualTo(0L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void shouldThrowExceptionWhenCompletePaymentOnNonPendingOrder() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 2)
        );
        Order order = new Order(userId, items);
        order.completePayment(0L); // 이미 PAID 상태

        // when & then
        assertThatThrownBy(() -> order.completePayment(0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order is not pending");
    }
}
