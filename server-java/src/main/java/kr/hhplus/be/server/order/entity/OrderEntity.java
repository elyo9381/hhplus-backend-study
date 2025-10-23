package kr.hhplus.be.server.order.entity;

import jakarta.persistence.*;
import kr.hhplus.be.server.payment.entity.PaymentStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order")
public class OrderEntity {

    protected OrderEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column
    private long userId;

    @Column
    private long amount;

    @Column
    private long price;

    @Column
    private long finalPrice;

    @Enumerated(value = EnumType.STRING)
    private OrderStatus status ;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> orderItems = new ArrayList<>();

    private LocalDateTime createdAt ;

    public void addOrderItem(OrderItemEntity item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void removeOrderItem(OrderItemEntity item) {
        orderItems.remove(item);
        item.setOrder(null);
    }


    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getAmount() {
        return amount;
    }

    public long getPrice() {
        return price;
    }

    public long getFinalPrice() {
        return finalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItemEntity> getOrderItems() {
        return orderItems;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

