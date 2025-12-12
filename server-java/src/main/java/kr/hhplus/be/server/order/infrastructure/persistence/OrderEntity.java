package kr.hhplus.be.server.order.infrastructure.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.order.domain.Order;
import kr.hhplus.be.server.order.domain.OrderItem;
import kr.hhplus.be.server.order.domain.OrderStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long discountAmount;

    @Column(nullable = false)
    private Long finalAmount;

    @Column(nullable = false)
    private Long paidAmount;

    @Column(nullable = false)
    private Long pointAmount;

    @Column(nullable = false)
    private Long remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Order toDomain() {
        List<OrderItem> domainItems = items.stream()
                .map(OrderItemEntity::toDomain)
                .toList();

        return new Order(
                id, userId, domainItems, totalAmount, discountAmount,
                finalAmount, paidAmount, pointAmount, remainingAmount,
                status, createdAt, updatedAt
        );
    }

    public static OrderEntity fromDomain(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.id = order.getId();
        entity.userId = order.getUserId();
        entity.totalAmount = order.getTotalAmount();
        entity.discountAmount = order.getDiscountAmount();
        entity.finalAmount = order.getFinalAmount();
        entity.paidAmount = order.getPaidAmount();
        entity.pointAmount = order.getPointAmount();
        entity.remainingAmount = order.getRemainingAmount();
        entity.status = order.getStatus();
        entity.createdAt = order.getCreatedAt();
        entity.updatedAt = order.getUpdatedAt();

        // OrderItem 매핑
        List<OrderItemEntity> itemEntities = order.getItems().stream()
                .map(item -> OrderItemEntity.fromDomain(item, entity))
                .toList();
        entity.items = itemEntities;

        return entity;
    }
}
