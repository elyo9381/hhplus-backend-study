package kr.hhplus.be.server.application.product;

import kr.hhplus.be.server.application.order.ProductPort;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductRepository;
import kr.hhplus.be.server.domain.product.ProductSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService implements ProductPort {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductEntity createProduct(String name, String description, BigDecimal price, int stock) {
        ProductEntity product = new ProductEntity(name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductEntity getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> getProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public void decreaseStock(UUID productId, int quantity) {
        ProductEntity product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        product.decreaseStock(quantity);
        // JPA 변경 감지로 자동 저장
    }

    @Override
    @Transactional
    public ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity) {
        ProductEntity product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        product.decreaseStock(quantity);
        
        return new ProductSnapshot(
                product.getId(),
                product.getName(),
                product.getPrice().longValue()
        );
    }
}
