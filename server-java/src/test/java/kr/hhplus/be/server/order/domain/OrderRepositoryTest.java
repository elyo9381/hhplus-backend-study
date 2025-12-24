package kr.hhplus.be.server.order.domain;

import kr.hhplus.be.server.TestContainerSupport;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderItem;
import kr.hhplus.be.server.domain.order.OrderRepository;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.infrastructure.order.persistence.OrderRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderRepositoryImpl.class)
class OrderRepositoryTest extends TestContainerSupport {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldSaveAndFindOrder() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 2),
                new OrderItem(UUID.randomUUID(), "Product B", 20000L, 1)
        );
        Order order = new Order(userId, items);

        // when
        Order saved = orderRepository.save(order);
        Optional<Order> found = orderRepository.findById(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getItems()).hasSize(2);
        assertThat(found.get().getTotalAmount()).isEqualTo(40000L);
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void shouldSaveOrderWithItems() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 3)
        );
        Order order = new Order(userId, items);

        // when
        Order saved = orderRepository.save(order);
        Order found = orderRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getItems()).hasSize(1);
        OrderItem item = found.getItems().get(0);
        assertThat(item.getProductName()).isEqualTo("Product A");
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getTotalPrice()).isEqualTo(30000L);
    }

    @Test
    void shouldUpdateOrderStatus() {
        // given
        UUID userId = UUID.randomUUID();
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), "Product A", 10000L, 2)
        );
        Order order = new Order(userId, items);
        Order saved = orderRepository.save(order);

        // when
        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        found.completePayment(5000L);
        Order updated = orderRepository.save(found);

        // then
        Order result = orderRepository.findById(updated.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.getPointAmount()).isEqualTo(5000L);
        assertThat(result.getPaidAmount()).isEqualTo(20000L);
    }
}
