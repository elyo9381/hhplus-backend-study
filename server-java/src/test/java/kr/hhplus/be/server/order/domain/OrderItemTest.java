package kr.hhplus.be.server.order.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void shouldCreateOrderItem() {
        // given
        UUID productId = UUID.randomUUID();
        String productName = "Test Product";
        Long unitPrice = 10000L;
        int quantity = 3;

        // when
        OrderItem item = new OrderItem(productId, productName, unitPrice, quantity);

        // then
        assertThat(item.getId()).isNotNull();
        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getProductName()).isEqualTo(productName);
        assertThat(item.getUnitPrice()).isEqualTo(unitPrice);
        assertThat(item.getQuantity()).isEqualTo(quantity);
        assertThat(item.getTotalPrice()).isEqualTo(30000L);
        assertThat(item.getDiscountAmount()).isEqualTo(0L);
        assertThat(item.getFinalPrice()).isEqualTo(30000L);
    }
}
