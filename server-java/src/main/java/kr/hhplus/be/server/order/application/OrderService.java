package kr.hhplus.be.server.order.application;

import kr.hhplus.be.server.order.application.dto.OrderItemRequest;
import kr.hhplus.be.server.order.domain.Order;
import kr.hhplus.be.server.order.domain.OrderItem;
import kr.hhplus.be.server.order.domain.OrderRepository;
import kr.hhplus.be.server.product.ProductService;
import kr.hhplus.be.server.product.ProductSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository, ProductService productService) {
        this.orderRepository = orderRepository;
        this.productService = productService;
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
            ProductSnapshot snapshot = productService.decreaseStockWithSnapshot(
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
