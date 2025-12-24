package kr.hhplus.be.server.product;

import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.domain.product.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldDecreaseStock() {
        // given
        ProductEntity product = new ProductEntity("Product", "desc", BigDecimal.valueOf(10000), 10);

        // when
        product.decreaseStock(3);

        // then
        assertThat(product.getStock()).isEqualTo(7);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    void shouldChangeToSoldOutWhenStockBecomesZero() {
        // given
        ProductEntity product = new ProductEntity("Product", "desc", BigDecimal.valueOf(10000), 3);

        // when
        product.decreaseStock(3);

        // then
        assertThat(product.getStock()).isEqualTo(0);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
    }

    @Test
    void shouldThrowExceptionWhenInsufficientStock() {
        // given
        ProductEntity product = new ProductEntity("Product", "desc", BigDecimal.valueOf(10000), 5);

        // when & then
        assertThatThrownBy(() -> product.decreaseStock(10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient stock");
    }
}
