package kr.hhplus.be.server.order.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Order {
    private final UUID id;
    private final UUID userId;
    private final List<OrderItem> items;
    private final Long totalAmount;
    private final Long discountAmount;
    private final Long finalAmount;
    private Long paidAmount;
    private Long pointAmount;
    private Long remainingAmount;
    private OrderStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 주문 생성 시 사용하는 생성자
    public Order(UUID userId, List<OrderItem> items) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.totalAmount = calculateTotalAmount();
        this.discountAmount = 0L;
        this.finalAmount = this.totalAmount;
        this.paidAmount = 0L;
        this.pointAmount = 0L;
        this.remainingAmount = this.finalAmount;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Entity에서 도메인으로 변환 시 사용하는 생성자
    public Order(UUID id, UUID userId, List<OrderItem> items,
                 Long totalAmount, Long discountAmount, Long finalAmount,
                 Long paidAmount, Long pointAmount, Long remainingAmount,
                 OrderStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.paidAmount = paidAmount;
        this.pointAmount = pointAmount;
        this.remainingAmount = remainingAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private Long calculateTotalAmount() {
        return items.stream()
                .mapToLong(OrderItem::getTotalPrice)
                .sum();
    }

    public void completePayment(Long pointAmount) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not pending");
        }
        this.pointAmount = pointAmount;
        this.paidAmount = this.finalAmount;
        this.remainingAmount = 0L;
        this.status = OrderStatus.PAID;
        this.updatedAt = LocalDateTime.now();
    }
}
