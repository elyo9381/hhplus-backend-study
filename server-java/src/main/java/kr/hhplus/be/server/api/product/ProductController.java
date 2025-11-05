package kr.hhplus.be.server.api.product;

import kr.hhplus.be.server.entity.product.ProductEntity;
import kr.hhplus.be.server.entity.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public List<ProductEntity> getProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public ProductEntity getProduct(@PathVariable UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));
    }
}
