package kr.hhplus.be.server.order.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.application.order.ProductPort;
import kr.hhplus.be.server.domain.order.*;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.product.ProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductPort productPort;

    @Mock
    private OutboxRepository outboxRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productPort, outboxRepository, new ObjectMapper());
    }

    @Test
    void shouldCreateOrderWithSingleProduct() {
        // given
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderItemRequest request = new OrderItemRequest(productId, 2);

        ProductSnapshot snapshot = new ProductSnapshot(productId, "Product A", 10000L);
        when(productPort.decreaseStockWithSnapshot(productId, 2)).thenReturn(snapshot);

        Order savedOrder = new Order(userId, List.of(
                new OrderItem(productId, "Product A", 10000L, 2)
        ));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // when
        Order result = orderService.createOrder(userId, List.of(request));

        // then
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalAmount()).isEqualTo(20000L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(productPort).decreaseStockWithSnapshot(productId, 2);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldCreateOrderWithMultipleProducts() {
        // given
        UUID userId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();

        List<OrderItemRequest> requests = List.of(
                new OrderItemRequest(productId1, 2),
                new OrderItemRequest(productId2, 3)
        );

        ProductSnapshot snapshot1 = new ProductSnapshot(productId1, "Product A", 10000L);
        ProductSnapshot snapshot2 = new ProductSnapshot(productId2, "Product B", 20000L);

        when(productPort.decreaseStockWithSnapshot(productId1, 2)).thenReturn(snapshot1);
        when(productPort.decreaseStockWithSnapshot(productId2, 3)).thenReturn(snapshot2);

        Order savedOrder = new Order(userId, List.of(
                new OrderItem(productId1, "Product A", 10000L, 2),
                new OrderItem(productId2, "Product B", 20000L, 3)
        ));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // when
        Order result = orderService.createOrder(userId, requests);

        // then
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotalAmount()).isEqualTo(80000L); // 20000 + 60000
        verify(productPort).decreaseStockWithSnapshot(productId1, 2);
        verify(productPort).decreaseStockWithSnapshot(productId2, 3);
    }

    @Test
    void shouldSortProductsByIdToPreventDeadlock() {
        // given
        UUID userId = UUID.randomUUID();
        UUID productId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID productId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // 역순으로 요청
        List<OrderItemRequest> requests = List.of(
                new OrderItemRequest(productId2, 1),
                new OrderItemRequest(productId1, 1)
        );

        ProductSnapshot snapshot1 = new ProductSnapshot(productId1, "Product A", 10000L);
        ProductSnapshot snapshot2 = new ProductSnapshot(productId2, "Product B", 20000L);

        when(productPort.decreaseStockWithSnapshot(productId1, 1)).thenReturn(snapshot1);
        when(productPort.decreaseStockWithSnapshot(productId2, 1)).thenReturn(snapshot2);

        Order savedOrder = new Order(userId, List.of(
                new OrderItem(productId1, "Product A", 10000L, 1),
                new OrderItem(productId2, "Product B", 20000L, 1)
        ));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // when
        orderService.createOrder(userId, requests);

        // then - productId1이 먼저 호출되어야 함 (정렬됨)
        var inOrder = inOrder(productPort);
        inOrder.verify(productPort).decreaseStockWithSnapshot(productId1, 1);
        inOrder.verify(productPort).decreaseStockWithSnapshot(productId2, 1);
    }

    @Test
    void shouldThrowExceptionWhenProductNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderItemRequest request = new OrderItemRequest(productId, 2);

        when(productPort.decreaseStockWithSnapshot(productId, 2))
                .thenThrow(new IllegalArgumentException("Product not found"));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, List.of(request)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product not found");
    }

    @Test
    void shouldThrowExceptionWhenInsufficientStock() {
        // given
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderItemRequest request = new OrderItemRequest(productId, 100);

        when(productPort.decreaseStockWithSnapshot(productId, 100))
                .thenThrow(new IllegalArgumentException("Insufficient stock"));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, List.of(request)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient stock");
    }
}
