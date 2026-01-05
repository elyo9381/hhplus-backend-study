# 캐시 전략 분석 보고서

## 분석 대상
- API: `GET /api/products` (상품 목록 전체 조회)
- 쿼리: `SELECT * FROM products`
- 데이터: 1,000개 상품

## 테스트 환경
- k6 부하테스트
- 100 VU (Virtual Users)
- 50초 실행

## 테스트 결과

### 캐시 없음 (No Cache)
| 지표 | 값 |
|------|-----|
| 총 요청 수 | 7,028 |
| 성공률 | 21.94% |
| 평균 응답시간 | 327.58ms |
| p(95) | 599.07ms |
| TPS | 140.55 |

### 캐시 적용 (Redis Cache)
| 지표 | 값 |
|------|-----|
| 총 요청 수 | 7,822 |
| 성공률 | 34.49% |
| 평균 응답시간 | 284.02ms |
| p(95) | 610.38ms |
| TPS | 156.14 |

### 비교
| 지표 | No Cache | With Cache | 개선율 |
|------|----------|------------|--------|
| 총 요청 수 | 7,028 | 7,822 | +11% |
| 성공률 | 21.94% | 34.49% | +57% |
| 평균 응답시간 | 327.58ms | 284.02ms | 13% ↓ |
| TPS | 140.55 | 156.14 | +11% |

## 캐시 설정
```java
@Cacheable(value = "products", key = "'all'")
public List<ProductEntity> getProducts() {
    return productRepository.findAll();
}
```

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5));  // TTL 5분
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

## 분석

### 캐시 효과가 제한적인 이유
1. **대용량 데이터 직렬화 비용**: 1,000개 상품 JSON 직렬화/역직렬화 오버헤드
2. **네트워크 비용**: Redis → App 간 데이터 전송
3. **단순 쿼리**: `SELECT *` 쿼리 자체가 빠름

### 캐시가 효과적인 케이스
- 복잡한 쿼리 (JOIN, GROUP BY, 집계)
- 소량 데이터 반복 조회
- 계산 비용이 큰 로직

## 결론
- 단순 목록 조회는 캐시 효과 제한적 (13% 개선)
- 페이징 처리로 응답 크기 줄이기 권장
- 집계 쿼리(인기 상품 등)에 캐시 적용 시 효과 극대화
