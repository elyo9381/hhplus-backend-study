package kr.hhplus.be.server.domain.payment;

import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.payment.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Payment {
    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private BigDecimal pointAmount;
    private PaymentType type;
    private PaymentStatus status;
    private String reason;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static Payment createPayment(UUID orderId, BigDecimal amount, BigDecimal pointAmount) {
        return new Payment(
                UUID.randomUUID(),
                orderId,
                amount,
                pointAmount,
                PaymentType.PAYMENT,
                PaymentStatus.SUCCESS,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    public static Payment createRefund(UUID orderId, BigDecimal amount, String reason) {
        return new Payment(
                UUID.randomUUID(),
                orderId,
                amount,
                BigDecimal.ZERO,
                PaymentType.REFUND,
                PaymentStatus.SUCCESS,
                reason,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    public boolean isSuccess() {
        return this.status == PaymentStatus.SUCCESS;
    }
}
