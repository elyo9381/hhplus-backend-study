package kr.hhplus.be.server.order.domain;

import lombok.Getter;

import java.util.UUID;

@Getter
public class OrderItem {
    private final UUID id;
    private final UUID productId;
    private final String productName;
    private final Long unitPrice;
    private final int quantity;
    private final Long totalPrice;
    private final Long discountAmount;
    private final Long finalPrice;

    // 주문 생성 시 사용
    public OrderItem(UUID productId, String productName, Long unitPrice, int quantity) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.totalPrice = unitPrice * quantity;
        this.discountAmount = 0L;
        this.finalPrice = this.totalPrice;
    }

    // Entity에서 도메인으로 변환 시 사용
    public OrderItem(UUID id, UUID productId, String productName,
                     Long unitPrice, int quantity, Long totalPrice,
                     Long discountAmount, Long finalPrice) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.discountAmount = discountAmount;
        this.finalPrice = finalPrice;
    }
}
