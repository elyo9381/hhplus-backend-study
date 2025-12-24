package kr.hhplus.be.server.product;

import kr.hhplus.be.server.domain.product.InsufficientStockException;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.product.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Product 도메인 로직 단위 테스트
 */
class ProductTest {

    @Test
    @DisplayName("상품 생성 시 재고가 있으면 SELLING 상태")
    void 상품_생성_시_재고가_있으면_SELLING_상태() {
        // Given & When
        Product product = new Product("상품명", "설명", 10000L, 10);

        // Then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("상품 생성 시 재고가 0이면 SOLDOUT 상태")
    void 상품_생성_시_재고가_0이면_SOLDOUT_상태() {
        // Given & When
        Product product = new Product("상품명", "설명", 10000L, 0);

        // Then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 차감 성공")
    void 재고_차감_성공() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 10);

        // When
        product.decreaseStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(7);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    @DisplayName("재고를 모두 차감하면 SOLDOUT 상태로 변경")
    void 재고를_모두_차감하면_SOLDOUT_상태로_변경() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 5);

        // When
        product.decreaseStock(5);

        // Then
        assertThat(product.getStock()).isEqualTo(0);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
    }

    @Test
    @DisplayName("재고 부족 시 예외 발생")
    void 재고_부족_시_예외_발생() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 5);

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(10))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("재고 부족");
    }

    @Test
    @DisplayName("0 이하 수량 차감 시 예외 발생")
    void 영_이하_수량_차감_시_예외_발생() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 10);

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다 커야");

        assertThatThrownBy(() -> product.decreaseStock(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다 커야");
    }

    @Test
    @DisplayName("재고 증가 성공")
    void 재고_증가_성공() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 5);

        // When
        product.increaseStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("SOLDOUT 상태에서 재고 증가 시 SELLING 상태로 변경")
    void SOLDOUT_상태에서_재고_증가_시_SELLING_상태로_변경() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 0);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLDOUT);

        // When
        product.increaseStock(5);

        // Then
        assertThat(product.getStock()).isEqualTo(5);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    @DisplayName("0 이하 수량 증가 시 예외 발생")
    void 영_이하_수량_증가_시_예외_발생() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 10);

        // When & Then
        assertThatThrownBy(() -> product.increaseStock(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다 커야");

        assertThatThrownBy(() -> product.increaseStock(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다 커야");
    }

    @Test
    @DisplayName("재고 검증 성공")
    void 재고_검증_성공() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 10);

        // When & Then
        assertThatCode(() -> product.validateStock(5))
                .doesNotThrowAnyException();

        assertThatCode(() -> product.validateStock(10))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("재고 검증 실패")
    void 재고_검증_실패() {
        // Given
        Product product = new Product("상품명", "설명", 10000L, 5);

        // When & Then
        assertThatThrownBy(() -> product.validateStock(10))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("재고 부족");
    }
}
