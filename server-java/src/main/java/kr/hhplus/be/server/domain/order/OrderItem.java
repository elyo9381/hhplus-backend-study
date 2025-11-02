package kr.hhplus.be.server.domain.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderItem {
    private UUID id;
    private UUID productId;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal totalPrice;
    private BigDecimal discountAmount;
    private BigDecimal finalPrice;

    public static OrderItem create(UUID productId, String productName, BigDecimal unitPrice, Integer quantity) {
        UUID id = UUID.randomUUID();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal finalPrice = totalPrice.subtract(discountAmount);

        return new OrderItem(
                id,
                productId,
                productName,
                unitPrice,
                quantity,
                totalPrice,
                discountAmount,
                finalPrice
        );
    }

    public void applyDiscount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
        this.finalPrice = this.totalPrice.subtract(discountAmount);
    }
}
