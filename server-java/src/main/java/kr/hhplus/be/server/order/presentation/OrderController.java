package kr.hhplus.be.server.order.presentation;

import kr.hhplus.be.server.order.application.OrderService;
import kr.hhplus.be.server.order.application.dto.CreateOrderRequest;
import kr.hhplus.be.server.order.application.dto.OrderResponse;
import kr.hhplus.be.server.order.domain.Order;
import kr.hhplus.be.server.payment.application.PaymentService;
import kr.hhplus.be.server.payment.application.dto.PaymentRequest;
import kr.hhplus.be.server.payment.application.dto.PaymentResponse;
import kr.hhplus.be.server.payment.domain.Payment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request.userId(), request.items());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/payment")
    public ResponseEntity<PaymentResponse> executePayment(
            @PathVariable UUID orderId,
            @RequestBody PaymentRequest request) {
        Payment payment = paymentService.executePayment(orderId, request.userId());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }
}
