package kr.hhplus.be.server.infrastructure.order.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.order.OrderItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private Long totalPrice;

    @Column(nullable = false)
    private Long discountAmount;

    @Column(nullable = false)
    private Long finalPrice;

    public OrderItem toDomain() {
        return new OrderItem(
                id, productId, productName, unitPrice, quantity,
                totalPrice, discountAmount, finalPrice
        );
    }

    public static OrderItemEntity fromDomain(OrderItem item, OrderEntity order) {
        OrderItemEntity entity = new OrderItemEntity();
        entity.id = item.getId();
        entity.order = order;
        entity.productId = item.getProductId();
        entity.productName = item.getProductName();
        entity.unitPrice = item.getUnitPrice();
        entity.quantity = item.getQuantity();
        entity.totalPrice = item.getTotalPrice();
        entity.discountAmount = item.getDiscountAmount();
        entity.finalPrice = item.getFinalPrice();
        return entity;
    }
}
