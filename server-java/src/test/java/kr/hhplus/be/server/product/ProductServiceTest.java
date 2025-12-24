package kr.hhplus.be.server.product;

import kr.hhplus.be.server.application.product.ProductService;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductJpaRepository;
import kr.hhplus.be.server.domain.product.ProductSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductJpaRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldGetProduct() {
        // given
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 100);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // when
        ProductEntity result = productService.getProduct(productId);

        // then
        assertThat(result.getName()).isEqualTo("Product A");
        assertThat(result.getPrice()).isEqualTo(BigDecimal.valueOf(10000));
        verify(productRepository).findById(productId);
    }

    @Test
    void shouldThrowExceptionWhenProductNotFound() {
        // given
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.getProduct(productId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product not found");
    }

    @Test
    void shouldGetAllProducts() {
        // given
        List<ProductEntity> products = List.of(
                new ProductEntity("Product A", "Desc A", BigDecimal.valueOf(10000), 100),
                new ProductEntity("Product B", "Desc B", BigDecimal.valueOf(20000), 50)
        );
        when(productRepository.findAll()).thenReturn(products);

        // when
        List<ProductEntity> result = productService.getProducts();

        // then
        assertThat(result).hasSize(2);
        verify(productRepository).findAll();
    }

    @Test
    void shouldCreateProduct() {
        // given
        String name = "New Product";
        String description = "New Description";
        BigDecimal price = BigDecimal.valueOf(15000);
        int stock = 50;
        
        ProductEntity savedProduct = new ProductEntity(name, description, price, stock);
        when(productRepository.save(org.mockito.ArgumentMatchers.any(ProductEntity.class))).thenReturn(savedProduct);

        // when
        ProductEntity result = productService.createProduct(name, description, price, stock);

        // then
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getPrice()).isEqualTo(price);
        assertThat(result.getStock()).isEqualTo(stock);
        verify(productRepository).save(org.mockito.ArgumentMatchers.any(ProductEntity.class));
    }

    @Test
    void shouldDecreaseStock() {
        // given
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 10);
        when(productRepository.findByIdWithLock(productId)).thenReturn(Optional.of(product));

        // when
        productService.decreaseStock(productId, 3);

        // then
        assertThat(product.getStock()).isEqualTo(7);
        verify(productRepository).findByIdWithLock(productId);
    }

    @Test
    void shouldThrowExceptionWhenProductNotFoundForDecrease() {
        // given
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdWithLock(productId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.decreaseStock(productId, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product not found");
    }

    @Test
    void shouldThrowExceptionWhenInsufficientStockForDecrease() {
        // given
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 5);
        when(productRepository.findByIdWithLock(productId)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> productService.decreaseStock(productId, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient stock");
    }

    @Test
    void shouldDecreaseStockWithSnapshot() {
        // given
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 10);
        when(productRepository.findByIdWithLock(productId)).thenReturn(Optional.of(product));

        // when
        ProductSnapshot snapshot = productService.decreaseStockWithSnapshot(productId, 3);

        // then
        assertThat(product.getStock()).isEqualTo(7);
        assertThat(snapshot.productId()).isEqualTo(product.getId());
        assertThat(snapshot.productName()).isEqualTo("Product A");
        assertThat(snapshot.unitPrice()).isEqualTo(10000L);
        verify(productRepository).findByIdWithLock(productId);
    }

    @Test
    void shouldThrowExceptionWhenProductNotFoundForSnapshot() {
        // given
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdWithLock(productId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.decreaseStockWithSnapshot(productId, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product not found");
    }

    @Test
    void shouldThrowExceptionWhenInsufficientStockForSnapshot() {
        // given
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 5);
        when(productRepository.findByIdWithLock(productId)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> productService.decreaseStockWithSnapshot(productId, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient stock");
    }
}
