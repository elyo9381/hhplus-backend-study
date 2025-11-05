package kr.hhplus.be.server.api.order;

import kr.hhplus.be.server.entity.order.OrderEntity;
import kr.hhplus.be.server.entity.order.OrderItemEntity;
import kr.hhplus.be.server.entity.order.OrderRepository;
import kr.hhplus.be.server.entity.product.ProductEntity;
import kr.hhplus.be.server.entity.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @PostMapping
    @Transactional
    public OrderEntity createOrder(@RequestBody CreateOrderRequest request) {
        OrderEntity order = new OrderEntity(request.getUserId());

        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            ProductEntity product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

            product.decreaseStock(itemRequest.getQuantity());

            OrderItemEntity orderItem = new OrderItemEntity(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    itemRequest.getQuantity()
            );

            order.addOrderItem(orderItem);
        }

        return orderRepository.save(order);
    }

    @GetMapping("/{id}")
    public OrderEntity getOrder(@PathVariable UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다"));
    }
}
