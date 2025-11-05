package kr.hhplus.be.server.entity.payment;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.payment.PaymentType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal pointAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String reason;

    private LocalDateTime paidAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (pointAmount == null) {
            pointAmount = BigDecimal.ZERO;
        }
    }

    public PaymentEntity(UUID orderId, BigDecimal amount, BigDecimal pointAmount, PaymentType type) {
        this.orderId = orderId;
        this.amount = amount;
        this.pointAmount = pointAmount;
        this.type = type;
        this.status = PaymentStatus.SUCCESS;
        this.paidAt = LocalDateTime.now();
    }
}
