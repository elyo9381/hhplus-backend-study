package kr.hhplus.be.server.product;

import kr.hhplus.be.server.domain.product.InsufficientStockException;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.product.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductDomainTest {

    @Nested
    @DisplayName("상품 생성")
    class CreateProduct {
        @Test
        @DisplayName("재고가 있으면 SELLING 상태")
        void 재고있으면_SELLING() {
            Product product = new Product("상품", "설명", 10000L, 10);
            
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
            assertThat(product.getStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("재고가 0이면 SOLDOUT 상태")
        void 재고없으면_SOLDOUT() {
            Product product = new Product("상품", "설명", 10000L, 0);
            
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
        }
    }

    @Nested
    @DisplayName("재고 차감")
    class DecreaseStock {
        @Test
        @DisplayName("정상 차감")
        void 정상_차감() {
            Product product = new Product("상품", "설명", 10000L, 10);
            
            product.decreaseStock(3);
            
            assertThat(product.getStock()).isEqualTo(7);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
        }

        @Test
        @DisplayName("재고 전부 차감 시 SOLDOUT")
        void 전부_차감시_SOLDOUT() {
            Product product = new Product("상품", "설명", 10000L, 5);
            
            product.decreaseStock(5);
            
            assertThat(product.getStock()).isEqualTo(0);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
        }

        @Test
        @DisplayName("재고 부족 시 예외")
        void 재고_부족_예외() {
            Product product = new Product("상품", "설명", 10000L, 5);
            
            assertThatThrownBy(() -> product.decreaseStock(10))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("재고 부족");
        }

        @Test
        @DisplayName("0 이하 수량 요청 시 예외")
        void 잘못된_수량_예외() {
            Product product = new Product("상품", "설명", 10000L, 10);
            
            assertThatThrownBy(() -> product.decreaseStock(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> product.decreaseStock(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("재고 추가")
    class IncreaseStock {
        @Test
        @DisplayName("정상 추가")
        void 정상_추가() {
            Product product = new Product("상품", "설명", 10000L, 10);
            
            product.increaseStock(5);
            
            assertThat(product.getStock()).isEqualTo(15);
        }

        @Test
        @DisplayName("SOLDOUT에서 재고 추가 시 SELLING")
        void SOLDOUT에서_추가시_SELLING() {
            Product product = new Product("상품", "설명", 10000L, 0);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
            
            product.increaseStock(5);
            
            assertThat(product.getStock()).isEqualTo(5);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
        }

        @Test
        @DisplayName("0 이하 수량 추가 시 예외")
        void 잘못된_수량_예외() {
            Product product = new Product("상품", "설명", 10000L, 10);
            
            assertThatThrownBy(() -> product.increaseStock(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
