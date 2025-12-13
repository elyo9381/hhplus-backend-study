package kr.hhplus.be.server.outbox;

import kr.hhplus.be.server.infrastructure.point.persistence.PointEntity;
import kr.hhplus.be.server.infrastructure.point.persistence.PointRepository;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OutboxTestHelper {

    private final ProductRepository productRepository;
    private final PointRepository pointRepository;
    private final JdbcTemplate jdbcTemplate;

    public OutboxTestHelper(ProductRepository productRepository,
                           PointRepository pointRepository,
                           JdbcTemplate jdbcTemplate) {
        this.productRepository = productRepository;
        this.pointRepository = pointRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID createTestProduct(String name, BigDecimal price, int stock) {
        UUID productId = UUID.randomUUID();
        
        jdbcTemplate.update(
                "INSERT INTO products (id, name, price, stock, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                productId, name, price, stock, LocalDateTime.now(), LocalDateTime.now()
        );
        
        return productId;
    }

    public void createTestPoint(UUID userId, Long amount) {
        jdbcTemplate.update(
                "INSERT INTO user_points (user_id, amount) VALUES (?, ?)",
                userId, amount
        );
    }
}
