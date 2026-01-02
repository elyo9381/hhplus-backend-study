package kr.hhplus.be.server.order.domain;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderItem;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.infrastructure.order.persistence.OrderEntity;
import kr.hhplus.be.server.infrastructure.order.persistence.OrderItemEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEntityTest {

    @Test
    void shouldConvertFromDomainToEntity() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 2),
                new OrderItem(UUID.randomUUID(), "Product B", 20000L, 1)
        );
        Order order = new Order(userId, items);

        // when
        OrderEntity entity = OrderEntity.fromDomain(order);

        // then
        assertThat(entity.getId()).isEqualTo(order.getId());
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getItems()).hasSize(2);
        assertThat(entity.getTotalAmount()).isEqualTo(40000L);
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void shouldConvertFromEntityToDomain() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 2)
        );
        Order originalOrder = new Order(userId, items);
        OrderEntity entity = OrderEntity.fromDomain(originalOrder);

        // when
        Order domain = entity.toDomain();

        // then
        assertThat(domain.getId()).isEqualTo(originalOrder.getId());
        assertThat(domain.getUserId()).isEqualTo(userId);
        assertThat(domain.getItems()).hasSize(1);
        assertThat(domain.getTotalAmount()).isEqualTo(20000L);
        assertThat(domain.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void shouldMaintainOrderItemRelationship() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 2)
        );
        Order order = new Order(userId, items);

        // when
        OrderEntity entity = OrderEntity.fromDomain(order);

        // then
        assertThat(entity.getItems()).hasSize(1);
        OrderItemEntity itemEntity = entity.getItems().get(0);
        assertThat(itemEntity.getOrder()).isEqualTo(entity);
        assertThat(itemEntity.getProductName()).isEqualTo("Product A");
    }
}
