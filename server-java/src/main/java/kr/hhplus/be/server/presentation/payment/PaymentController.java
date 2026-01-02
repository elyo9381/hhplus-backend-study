package kr.hhplus.be.server.presentation.payment;

import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.application.payment.dto.PaymentRequest;
import kr.hhplus.be.server.application.payment.dto.PaymentResponse;
import kr.hhplus.be.server.domain.payment.Payment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> executePayment(@RequestBody PaymentRequest request) {
        Payment payment = paymentService.executePayment(
                request.orderId(), 
                request.userId(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }
}
