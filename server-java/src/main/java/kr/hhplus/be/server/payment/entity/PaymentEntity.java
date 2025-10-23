package kr.hhplus.be.server.payment.entity;

import jakarta.persistence.*;
import kr.hhplus.be.server.product.entity.ProductType;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    protected PaymentEntity(){}

    public PaymentEntity(long userId, long amount, PaymentStatus status) {
        this.userId = userId;
        this.amount = amount;
        this.status = status;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column
    private long userId;

    @Column
    private long amount;

    @Enumerated(value = EnumType.STRING)
    private PaymentStatus status ;

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
