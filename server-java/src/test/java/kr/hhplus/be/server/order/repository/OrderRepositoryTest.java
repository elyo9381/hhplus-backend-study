package kr.hhplus.be.server.order.repository;

import kr.hhplus.be.server.order.entity.OrderEntity;
import kr.hhplus.be.server.order.entity.OrderItemEntity;
import kr.hhplus.be.server.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.utility.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void 주문_생성_테스트() {
        // given
        OrderEntity order = new OrderEntity(1L, 2L, 10000L, 20000L, OrderStatus.PENDING);
        OrderItemEntity item = new OrderItemEntity(100L, 10000L, 2L);
        order.addOrderItem(item);

        // when
        OrderEntity saved = orderRepository.save(order);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getOrderItems()).hasSize(1);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 주문_조회_테스트() {
        // given
        OrderEntity order = new OrderEntity(1L, 1L, 5000L, 5000L, OrderStatus.PENDING);
        OrderEntity saved = orderRepository.save(order);

        // when
        OrderEntity found = orderRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getUserId()).isEqualTo(1L);
    }

    @Test
    void 주문_아이템_cascade_테스트() {
        // given
        OrderEntity order = new OrderEntity(1L, 2L, 10000L, 20000L, OrderStatus.PENDING);
        OrderItemEntity item1 = new OrderItemEntity(100L, 10000L, 1L);
        OrderItemEntity item2 = new OrderItemEntity(200L, 5000L, 2L);
        order.addOrderItem(item1);
        order.addOrderItem(item2);

        // when
        OrderEntity saved = orderRepository.save(order);

        // then
        OrderEntity found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getOrderItems()).hasSize(2);
        assertThat(found.getOrderItems().get(0).getOrder()).isEqualTo(found);
    }
}
