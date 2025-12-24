package kr.hhplus.be.server.presentation.product;

import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.application.product.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/api/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@RequestBody ProductRequest request) {
        Product product = productService.createProduct(
                request.name(),
                request.description(),
                request.price(),
                request.stock()
        );
        return ProductResponse.from(product);
    }

    @GetMapping("/api/products/{id}")
    public ProductResponse getProduct(@PathVariable UUID id) {
        Product product = productService.getProduct(id);
        return ProductResponse.from(product);
    }
}
