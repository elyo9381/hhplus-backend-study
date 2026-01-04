package kr.hhplus.be.server.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderItem;
import kr.hhplus.be.server.domain.order.OrderRepository;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.product.ProductSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductPort productPort;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, 
                       ProductPort productPort,
                       OutboxRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.productPort = productPort;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 주문 생성
     * 
     * 동시성 제어:
     * - 비관적 락: ProductPort.decreaseStockWithSnapshot() 내부에서 FOR UPDATE
     * - 데드락 방지: productId 정렬로 락 획득 순서 일관성 보장 (ADR-021)
     * - 트랜잭션: 재고 차감 + 주문 저장 + Outbox 저장 원자성 보장
     */
    @Transactional
    public Order createOrder(UUID userId, List<OrderItemRequest> itemRequests) {
        // 데드락 방지: productId 순으로 정렬 (ADR-021)
        List<OrderItemRequest> sortedRequests = itemRequests.stream()
                .sorted(Comparator.comparing(OrderItemRequest::productId))
                .toList();

        // 재고 차감 + 스냅샷 획득 (비관적 락 적용)
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequest request : sortedRequests) {
            ProductSnapshot snapshot = productPort.decreaseStockWithSnapshot(
                    request.productId(),
                    request.quantity()
            );

            OrderItem orderItem = new OrderItem(
                    snapshot.productId(),
                    snapshot.productName(),
                    snapshot.unitPrice(),
                    request.quantity()
            );
            orderItems.add(orderItem);
        }

        // Order 생성 및 저장
        Order order = new Order(userId, orderItems);
        Order savedOrder = orderRepository.save(order);

        // Outbox 이벤트 저장 (같은 트랜잭션)
        Outbox outbox = new Outbox(
                "ORDER_CREATED",
                savedOrder.getId(),
                toJson(Map.of(
                        "orderId", savedOrder.getId().toString(),
                        "userId", savedOrder.getUserId().toString(),
                        "totalAmount", savedOrder.getTotalAmount(),
                        "status", savedOrder.getStatus().name()
                ))
        );
        outboxRepository.save(outbox);

        return savedOrder;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
