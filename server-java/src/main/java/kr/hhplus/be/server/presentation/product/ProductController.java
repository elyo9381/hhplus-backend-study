package kr.hhplus.be.server.presentation.product;

import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.ProductRankingRepository;
import kr.hhplus.be.server.infrastructure.product.ProductRankingRepository.RankingEntry;
import kr.hhplus.be.server.application.product.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ProductController {

    private final ProductService productService;
    private final ProductRankingRepository productRankingRepository;

    public ProductController(ProductService productService,
                            ProductRankingRepository productRankingRepository) {
        this.productService = productService;
        this.productRankingRepository = productRankingRepository;
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

    /**
     * 일별 인기 상품 랭킹 조회
     */
    @GetMapping("/api/products/ranking/daily")
    public List<ProductRankingResponse> getDailyRanking(
            @RequestParam(defaultValue = "10") int limit) {
        List<RankingEntry> ranking = productRankingRepository.getDailyRanking(limit);
        return toRankingResponse(ranking);
    }

    /**
     * 주별 인기 상품 랭킹 조회
     */
    @GetMapping("/api/products/ranking/weekly")
    public List<ProductRankingResponse> getWeeklyRanking(
            @RequestParam(defaultValue = "10") int limit) {
        List<RankingEntry> ranking = productRankingRepository.getWeeklyRanking(limit);
        return toRankingResponse(ranking);
    }

    private List<ProductRankingResponse> toRankingResponse(List<RankingEntry> ranking) {
        return ranking.stream()
                .map(entry -> {
                    try {
                        ProductEntity product = productService.getProduct(entry.productId());
                        return new ProductRankingResponse(
                                entry.rank(),
                                entry.productId(),
                                product.getName(),
                                product.getPrice(),
                                entry.score()
                        );
                    } catch (Exception e) {
                        return new ProductRankingResponse(
                                entry.rank(),
                                entry.productId(),
                                "Unknown",
                                null,
                                entry.score()
                        );
                    }
                })
                .toList();
    }
}
