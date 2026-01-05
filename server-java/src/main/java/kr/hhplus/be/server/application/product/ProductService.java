package kr.hhplus.be.server.application.product;

import kr.hhplus.be.server.application.order.ProductPort;
import kr.hhplus.be.server.domain.product.ProductSnapshot;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductJpaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * ProductService
 * 
 * 레이어드 아키텍처: ProductEntity 직접 사용
 * 클린 아키텍처 호환: ProductPort 구현 (ProductSnapshot 반환)
 */
@Service
public class ProductService implements ProductPort {

    private final ProductJpaRepository productRepository;

    public ProductService(ProductJpaRepository productRepository) {
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
    @Cacheable(value = "products", key = "'all'")
    public List<ProductEntity> getProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public void decreaseStock(UUID productId, int quantity) {
        ProductEntity product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        product.decreaseStock(quantity);
        productRepository.save(product);
    }

    @Transactional
    public void increseStock(UUID productId, int quantity) {
        ProductEntity product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        product.increseStock(quantity);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity) {
        ProductEntity product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        product.decreaseStock(quantity);
        productRepository.save(product);
        
        return new ProductSnapshot(
                product.getId(),
                product.getName(),
                product.getPrice().longValue()
        );
    }
}
