package kr.hhplus.be.server.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        ProductEntity product = productService.createProduct(
                request.name(),
                request.description(),
                request.price(),
                request.stock()
        );
        return ProductResponse.from(product);
    }

    @GetMapping("/api/products/{id}")
    public ProductResponse getProduct(@PathVariable UUID id) {
        ProductEntity product = productService.getProduct(id);
        return ProductResponse.from(product);
    }

    @GetMapping("/api/products")
    public List<ProductResponse> getProducts() {
        return productService.getProducts().stream()
                .map(ProductResponse::from)
                .toList();
    }
}
