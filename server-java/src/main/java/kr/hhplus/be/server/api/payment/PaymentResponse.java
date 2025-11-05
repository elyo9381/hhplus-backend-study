package kr.hhplus.be.server.api.payment;

import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.payment.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private BigDecimal pointAmount;
    private PaymentType type;
    private PaymentStatus status;
    private String reason;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
