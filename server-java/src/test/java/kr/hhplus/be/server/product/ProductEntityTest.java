package kr.hhplus.be.server.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductEntityTest {

    @Test
    void shouldCreateProductWithSellingStatus() {
        // given
        String name = "Test Product";
        String description = "Test Description";
        BigDecimal price = BigDecimal.valueOf(10000);
        int stock = 100;

        // when
        ProductEntity product = new ProductEntity(name, description, price, stock);

        // then
        assertThat(product.getId()).isNotNull();
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getDescription()).isEqualTo(description);
        assertThat(product.getPrice()).isEqualTo(price);
        assertThat(product.getStock()).isEqualTo(stock);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldHaveSoldOutStatusWhenStockIsZero() {
        // given
        String name = "Test Product";
        BigDecimal price = BigDecimal.valueOf(10000);
        int stock = 0;

        // when
        ProductEntity product = new ProductEntity(name, "desc", price, stock);

        // then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
    }
}
