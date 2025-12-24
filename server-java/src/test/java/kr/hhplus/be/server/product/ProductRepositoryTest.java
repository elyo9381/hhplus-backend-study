package kr.hhplus.be.server.product;

import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0.40");

    @Autowired
    private ProductJpaRepository productRepository;

    @Test
    void shouldSaveAndFindProduct() {
        // given
        ProductEntity product = new ProductEntity("Product A", "Description A", BigDecimal.valueOf(10000), 100);

        // when
        ProductEntity saved = productRepository.save(product);
        Optional<ProductEntity> found = productRepository.findById(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Product A");
        assertThat(found.get().getPrice()).isEqualTo(BigDecimal.valueOf(10000));
        assertThat(found.get().getStock()).isEqualTo(100);
    }

    @Test
    void shouldFindAllProducts() {
        // given
        productRepository.save(new ProductEntity("Product A", "Desc A", BigDecimal.valueOf(10000), 100));
        productRepository.save(new ProductEntity("Product B", "Desc B", BigDecimal.valueOf(20000), 50));

        // when
        List<ProductEntity> products = productRepository.findAll();

        // then
        assertThat(products).hasSize(2);
    }
}
