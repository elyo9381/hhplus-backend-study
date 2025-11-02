package kr.hhplus.be.server.domain.order;

import kr.hhplus.be.server.domain.order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Order {
    private UUID id;
    private UUID userId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private BigDecimal paidAmount;
    private BigDecimal pointAmount;
    private BigDecimal remainingAmount;
    private OrderStatus status;
    private List<OrderItem> orderItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Order create(UUID userId) {
        return new Order(
                UUID.randomUUID(),
                userId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderStatus.PENDING,
                new ArrayList<>(),
                LocalDateTime.now(),
                null
        );
    }

    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        calculateAmounts();
    }

    private void calculateAmounts() {
        this.totalAmount = orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.discountAmount = orderItems.stream()
                .map(OrderItem::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.finalAmount = totalAmount.subtract(discountAmount);
        this.remainingAmount = finalAmount.subtract(paidAmount).subtract(pointAmount);
    }

    public void markAsPaid(BigDecimal paidAmount, BigDecimal pointAmount) {
        this.paidAmount = paidAmount;
        this.pointAmount = pointAmount;
        this.remainingAmount = finalAmount.subtract(paidAmount).subtract(pointAmount);
        this.status = OrderStatus.PAID;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean canBePaid() {
        return this.status == OrderStatus.PENDING;
    }
}
