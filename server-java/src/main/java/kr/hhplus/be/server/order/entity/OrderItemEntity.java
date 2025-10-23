package kr.hhplus.be.server.order.entity;


import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "order_item")
public class OrderItemEntity {

    protected OrderItemEntity(){}

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    @Column
    private long productId;

    @Column
    private long basePrice;

    @Column
    private long quantity;

    @Column
    private long totalPrice;

    private LocalDateTime createdAt ;

    void setOrder(OrderEntity order) {
        this.order = order;
    }

    public long getId() {
        return id;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public long getProductId() {
        return productId;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getTotalPrice() {
        return totalPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

