package kr.hhplus.be.server.entity.order;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.order.OrderStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private BigDecimal finalAmount;

    @Column(nullable = false)
    private BigDecimal paidAmount;

    @Column(nullable = false)
    private BigDecimal pointAmount;

    @Column(nullable = false)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> orderItems = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OrderStatus.PENDING;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
        if (pointAmount == null) {
            pointAmount = BigDecimal.ZERO;
        }
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public OrderEntity(UUID userId) {
        this.userId = userId;
        this.totalAmount = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.finalAmount = BigDecimal.ZERO;
        this.paidAmount = BigDecimal.ZERO;
        this.pointAmount = BigDecimal.ZERO;
        this.remainingAmount = BigDecimal.ZERO;
    }

    public void addOrderItem(OrderItemEntity orderItem) {
        orderItems.add(orderItem);
        orderItem.setOrder(this);
        calculateAmounts();
    }

    private void calculateAmounts() {
        this.totalAmount = orderItems.stream()
                .map(OrderItemEntity::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.discountAmount = orderItems.stream()
                .map(OrderItemEntity::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.finalAmount = totalAmount.subtract(discountAmount);
        this.remainingAmount = finalAmount.subtract(paidAmount).subtract(pointAmount);
    }

    public void markAsPaid(BigDecimal paidAmount, BigDecimal pointAmount) {
        this.paidAmount = paidAmount;
        this.pointAmount = pointAmount;
        this.remainingAmount = finalAmount.subtract(paidAmount).subtract(pointAmount);
        this.status = OrderStatus.PAID;
    }
}
