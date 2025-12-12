package kr.hhplus.be.server.payment.infrastructure.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.payment.domain.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long pointAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PaymentEntity(UUID id, UUID orderId, UUID userId, PaymentType paymentType,
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

    public Payment toDomain() {
        return new Payment(
                this.id,
                this.orderId,
                this.userId,
                this.paymentType,
                this.amount,
                this.pointAmount,
                this.status,
                this.createdAt,
                this.updatedAt
        );
    }

    public static PaymentEntity fromDomain(Payment payment) {
        return new PaymentEntity(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getPaymentType(),
                payment.getAmount(),
                payment.getPointAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
