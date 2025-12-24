package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.infrastructure.point.persistence.PointEntity;
import kr.hhplus.be.server.infrastructure.point.persistence.PointRepository;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductJpaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OutboxTestHelper {

    private final ProductJpaRepository productRepository;
    private final PointRepository pointRepository;

    public OutboxTestHelper(ProductJpaRepository productRepository,
                           PointRepository pointRepository) {
        this.productRepository = productRepository;
        this.pointRepository = pointRepository;
    }

    public UUID createTestProduct(String name, BigDecimal price, int stock) {
        ProductEntity product = new ProductEntity(name, "test", price, stock);
        ProductEntity saved = productRepository.save(product);
        return saved.getId();
    }

    public void createTestPoint(UUID userId, Long amount) {
        PointEntity point = new PointEntity(userId, amount, LocalDateTime.now().plusYears(1));
        pointRepository.save(point);
    }
}
