package kr.hhplus.be.server.application.product;

import kr.hhplus.be.server.application.order.ProductPort;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.product.ProductRepository;
import kr.hhplus.be.server.domain.product.ProductSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService implements ProductPort {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product createProduct(String name, String description, Long price, Integer stock) {
        Product product = new Product(name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    @Transactional
    public void decreaseStock(UUID productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        product.decreaseStock(quantity);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        product.decreaseStock(quantity);
        productRepository.save(product);
        
        return new ProductSnapshot(
                product.getId(),
                product.getName(),
                product.getPrice()
        );
    }
}
