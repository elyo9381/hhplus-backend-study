package kr.hhplus.be.server.application.product;

import kr.hhplus.be.server.domain.product.ProductSnapshot;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final RedisTemplate<String,String> redisTemplate;
    private final ProductService productService;

    public ProductEntity createProduct(String name, String description, BigDecimal price, int stock) {
        return productService.createProduct(name, description, price, stock);
    }


    public ProductEntity getProduct(UUID id) {
        return productService.getProduct(id);
    }


    public List<ProductEntity> getProducts() {
        return productService.getProducts();
    }

    // 분산락 단일 시도
    public void decreaseStock(UUID productId, int quantity) {
        String lockKey = "lock:product:" + productId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = false;
        try{
            locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 15, TimeUnit.SECONDS);
            if(!locked){
                throw new IllegalStateException("Lock acquisition failed");
            }
            productService.decreaseStock(productId, quantity);
        } finally {
            if(locked) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    // 분산락 풀링 시도
    public ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity) {
        String lockKey = "lock:product:" + productId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = false;

        int retryCount = 3;
        while(retryCount-- > 0){
            locked = Boolean.TRUE.equals(
                    redisTemplate
                            .opsForValue()
                            .setIfAbsent(lockKey, lockValue, 15, TimeUnit.SECONDS)
            );
            if(locked) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
        }

        if(!locked){
            throw new IllegalStateException("Lock acquisition failed");
        }

        try {
            return productService.decreaseStockWithSnapshot(productId,quantity);
        }finally {
            redisTemplate.delete(lockKey);
        }
    }

    // 분산락 pubsub 시도
    public void increseStock(UUID productId, int quantity) {
        String lockKey = "lock:product:" + productId;
        String lockValue = UUID.randomUUID().toString();
        String channel  = "lock:release:" + productId;

        boolean locked = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 15, TimeUnit.SECONDS));

        if(!locked) {
            CountDownLatch latch = new CountDownLatch(1);
            redisTemplate.getConnectionFactory().getConnection()
                    .subscribe((message, patten) -> latch.countDown(), channel.getBytes());

            try{
                latch.await(5,TimeUnit.SECONDS);
            } catch (InterruptedException e ){
                Thread.currentThread().interrupt();
            }

            locked = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 15, TimeUnit.SECONDS));
        }

        if(!locked){
            throw new IllegalStateException("Lock acquisition failed");
        }

        try{
            productService.increseStock(productId,quantity);
        } finally {
            redisTemplate.delete(lockKey);
            redisTemplate.convertAndSend(channel,"released");
        }
    }
}


