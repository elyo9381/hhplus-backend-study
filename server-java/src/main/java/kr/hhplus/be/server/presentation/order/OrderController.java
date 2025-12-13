package kr.hhplus.be.server.presentation.order;

import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.application.order.dto.CreateOrderRequest;
import kr.hhplus.be.server.application.order.dto.OrderResponse;
import kr.hhplus.be.server.domain.order.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request.userId(), request.items());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order));
    }
}
