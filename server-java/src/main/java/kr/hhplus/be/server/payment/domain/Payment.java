package kr.hhplus.be.server.payment.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class Payment {
    private final UUID id;
    private final UUID orderId;
    private final UUID userId;
    private final PaymentType paymentType;
    private final Long amount;
    private final Long pointAmount;
    private PaymentStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 결제 생성 시 사용하는 생성자
    public Payment(UUID orderId, UUID userId, Long amount, Long pointAmount) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.userId = userId;
        this.paymentType = PaymentType.POINT;
        this.amount = amount;
        this.pointAmount = pointAmount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Entity에서 도메인으로 변환 시 사용하는 생성자
    public Payment(UUID id, UUID orderId, UUID userId, PaymentType paymentType,
                   Long amount, Long pointAmount, PaymentStatus status,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.paymentType = paymentType;
        this.amount = amount;
        this.pointAmount = pointAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void complete() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not pending");
        }
        this.status = PaymentStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not pending");
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }
}
