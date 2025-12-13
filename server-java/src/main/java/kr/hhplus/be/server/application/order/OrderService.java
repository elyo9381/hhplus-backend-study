package kr.hhplus.be.server.application.order;

import kr.hhplus.be.server.application.order.dto.OrderItemRequest;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderItem;
import kr.hhplus.be.server.domain.order.OrderRepository;
import kr.hhplus.be.server.domain.product.ProductSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductPort productPort;

    public OrderService(OrderRepository orderRepository, ProductPort productPort) {
        this.orderRepository = orderRepository;
        this.productPort = productPort;
    }

    @Transactional
    public Order createOrder(UUID userId, List<OrderItemRequest> itemRequests) {
        // 데드락 방지: productId 순으로 정렬
        List<OrderItemRequest> sortedRequests = itemRequests.stream()
                .sorted(Comparator.comparing(OrderItemRequest::productId))
                .toList();

        // 재고 차감 + 스냅샷 획득
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
        return orderRepository.save(order);
    }
}
