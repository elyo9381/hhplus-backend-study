---
inclusion: always
---

# Coding Preference

Java 17 / Spring Boot 3.4 기반 이커머스 백엔드 프로젝트입니다.

---

## 🚀 매직 키워드 (oh-my-opencode 영감)

| 키워드 | 동작 | 비용 |
|--------|------|------|
| `ulw` / `ultrawork` | 전체 워크플로우 (탐색 → 계획 → 실행 → 검토 → 완료까지) | 높음 |
| `qf` / `quickfix` | 빠른 수정 (Player만, Coach 스킵) | 낮음 |
| `explore` | 백그라운드 탐색만 실행 | 중간 |

---

## 🔄 자동화 워크플로우

### 1. 자동 컨텍스트 수집 (ulw 모드)
복잡한 태스크 시작 시 자동으로:
```
요청 수신 즉시:
├── delegate(explorer) → 관련 파일 탐색 (비동기)
├── delegate(librarian) → 관련 문서/패턴 검색 (비동기)
└── 메인 작업 준비

결과 합류 후 → player 실행 (풍부한 컨텍스트)
```

### 2. 병렬 Coach 검토 (HIGH/CRITICAL 복잡도)
```
Player 완료 후:
├── delegate(security-coach) → 보안 검토
├── delegate(perf-coach) → 성능 검토
└── delegate(style-coach) → 스타일 검토

모두 완료 → 피드백 통합 → 필요시 재작업
```

### 3. 자동 디버깅 루프
```
빌드/테스트 실패 시:
├── delegate(oracle) → 원인 분석
├── delegate(explorer) → 유사 패턴 검색
└── 분석 결과로 player 재실행 (최대 3회)
```

### 4. 문서 기반 구현
```
외부 라이브러리 사용 요청 시:
├── delegate(librarian) → 공식 문서 + GitHub 예제 수집
└── 수집된 컨텍스트로 player 실행
```

### 5. 멀티 파일 병렬 수정
```
여러 파일 일괄 수정 요청 시:
├── delegate(explorer) → 대상 파일 목록 수집
├── 각 파일별 use_subagent(player) 병렬 실행
└── 결과 통합
```

### 6. 자동 회귀 테스트
```
코드 수정 후:
├── 관련 테스트 자동 실행
├── 실패 시 delegate(oracle) → 분석
└── 자동 수정 시도 (최대 3회)
```

---

## ✅ Todo Enforcer (작업 완료 강제)

- 모든 태스크가 완료될 때까지 대화 종료 금지
- 중간에 멈추면 자동으로 "계속 진행하세요" 
- 3회 연속 진전 없으면 에스컬레이션 (ESCALATION.md 생성)

---

## 📝 Comment Checker (코드 품질)

AI 생성 코드가 사람 코드와 구분 불가해야 함:
- 불필요한 주석 금지 (코드로 설명 가능한 내용)
- TODO/FIXME 외 임시 주석 금지
- 과도한 설명 주석 금지

---

## 🏷️ 카테고리별 자동 위임

| 키워드 감지 | 에이전트 | 용도 |
|-------------|----------|------|
| UI, 프론트, 화면, CSS | frontend-player | 프론트엔드 |
| 성능, 최적화, N+1, 캐시 | perf-coach | 성능 분석 |
| 보안, 인증, 권한, XSS | security-coach | 보안 검토 |
| 문서, API, 스펙, 예제 | librarian | 문서 검색 |
| 버그, 에러, 디버깅 | oracle | 원인 분석 |

---

## 프로젝트 구조

```
src/main/java/kr/hhplus/be/server/
├── domain/           # 도메인 모델, Repository 인터페이스, 비즈니스 로직
│   ├── order/        # Order, OrderItem, OrderRepository
│   ├── product/      # Product, ProductSnapshot, ProductRepository
│   ├── payment/      # Payment, PaymentRepository
│   ├── point/        # (infrastructure에 Entity로 존재)
│   ├── coupon/       # Coupon, UserCoupon, CouponRepository
│   └── outbox/       # Outbox, OutboxRepository
├── application/      # 서비스, 유스케이스, Port 인터페이스
│   ├── order/        # OrderService, ProductPort
│   ├── product/      # ProductService, ProductFacade
│   ├── payment/      # PaymentService, PointPort
│   ├── point/        # PointService
│   ├── coupon/       # CouponService
│   └── outbox/       # OutboxPublisher
├── infrastructure/   # JPA 구현체, Redis, 외부 연동
│   ├── */persistence # Entity, JpaRepository, RepositoryImpl
│   ├── coupon/       # CouponRedisRepository
│   ├── product/      # ProductRankingRepository
│   └── outbox/       # WebClientMessageProducer
├── presentation/     # Controller, Request/Response DTO
└── config/           # RedissonConfig, CacheConfig, WebClientConfig
```

## 도메인 모델 규칙

### Rich Domain Model
```java
// ✅ 비즈니스 로직은 도메인 객체 내부에
public class Order {
    public void completePayment(Long pointAmount) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not pending");
        }
        this.status = OrderStatus.PAID;
    }
}

// ❌ 서비스에서 상태 직접 변경 금지
order.setStatus(OrderStatus.PAID);  // 안티패턴
```

### 생성자 패턴
```java
// 신규 생성용 (UUID 자동 생성)
public Order(UUID userId, List<OrderItem> items) {
    this.id = UUID.randomUUID();
    // ...
}

// Entity → Domain 변환용 (모든 필드 주입)
public Order(UUID id, UUID userId, ..., LocalDateTime createdAt) {
    this.id = id;
    // ...
}
```

### 도메인 간 참조
- 같은 Aggregate 내: 직접 참조 (`Order` → `OrderItem`)
- 다른 Aggregate: ID 참조 (`Order.userId`, `Payment.orderId`)

## 서비스 레이어 규칙

### Port 인터페이스
```java
// application/order/ProductPort.java
public interface ProductPort {
    ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity);
}

// application/product/ProductService.java
@Service
public class ProductService implements ProductPort {
    @Override
    @Transactional
    public ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity) {
        // 비관적 락으로 재고 차감
    }
}
```

### 트랜잭션 경계
```java
// ✅ Service 메서드에만 @Transactional
@Service
public class OrderService {
    @Transactional
    public Order createOrder(UUID userId, List<OrderItemRequest> items) {
        // 재고 차감 + 주문 저장 + Outbox 저장 (원자성)
    }
}

// ❌ Controller에 @Transactional 금지 (HTTP 커넥션 점유)
```

## 동시성 제어

### 비관적 락 (재고, 포인트, 주문)
```java
// Repository 인터페이스
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM ProductEntity p WHERE p.id = :id")
Optional<ProductEntity> findByIdWithLock(@Param("id") UUID id);
```

### 데드락 방지 (ADR-021)
```java
// 상품 ID 정렬 후 락 획득
List<OrderItemRequest> sortedRequests = itemRequests.stream()
    .sorted(Comparator.comparing(OrderItemRequest::productId))
    .toList();

for (OrderItemRequest request : sortedRequests) {
    productPort.decreaseStockWithSnapshot(request.productId(), request.quantity());
}
```

### Redisson 분산락 (선착순 쿠폰)
```java
RLock lock = redissonClient.getLock("coupon:lock:" + couponId);
try {
    if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("락 획득 실패");
    }
    // 발급 로직
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

## Redis 전략

| 용도 | 전략 | 키 패턴 |
|------|------|---------|
| 쿠폰 생성 | Write-Through | `coupon:info:{id}` (Hash) |
| 쿠폰 발급 | Redis as SoT | `coupon:issued:{id}` (Set) |
| 분산락 | Redisson Lock | `coupon:lock:{id}` |
| 랭킹 | Redis Only | `product:ranking:daily:{date}` (Sorted Set) |

### Redis-DB 정합성 (TransactionSynchronization)
```java
@Transactional
public UserCoupon issueCoupon(UUID couponId, UUID userId) {
    // 1. Redis 발급
    couponRedisRepository.tryIssue(couponId, userId);
    
    // 2. 롤백 시 Redis 자동 롤백 등록
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    couponRedisRepository.rollback(couponId, userId);
                }
            }
        }
    );
    
    // 3. DB 저장
    return userCouponRepository.save(userCoupon);
}
```

## Outbox 패턴

```java
@Transactional
public Order createOrder(...) {
    Order savedOrder = orderRepository.save(order);
    
    // 같은 트랜잭션에서 Outbox 저장
    Outbox outbox = new Outbox("ORDER_CREATED", savedOrder.getId(), toJson(...));
    outboxRepository.save(outbox);
    
    return savedOrder;
}

// 스케줄러가 PENDING 상태 이벤트 발행
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    outboxPublisher.publishPendingEvents();
}
```

## 테스트 구조

```
src/test/java/kr/hhplus/be/server/
├── {domain}/
│   ├── {Domain}Test.java              # 도메인 단위 테스트
│   ├── {Domain}ServiceTest.java       # 서비스 Mock 테스트
│   ├── {Domain}RepositoryTest.java    # Repository 통합 테스트
│   ├── {Domain}ConcurrencyTest.java   # 동시성 테스트
│   └── {Domain}IntegrationTest.java   # 전체 통합 테스트
├── integration/
│   ├── FullFlowIntegrationTest.java   # E2E 플로우 테스트
│   └── ConcurrencyIntegrationTest.java
├── TestContainerSupport.java          # Testcontainers 설정
└── AbstractIntegrationTest.java       # 통합 테스트 베이스
```

### 테스트 패턴
```java
// 동시성 테스트
@Test
void 선착순_쿠폰_발급_수량만큼만_성공() throws InterruptedException {
    int threadCount = 20;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                couponService.issueCoupon(couponId, UUID.randomUUID());
                successCount.incrementAndGet();
            } catch (Exception e) {
                // 실패 카운트
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await();
    
    assertThat(successCount.get()).isEqualTo(10);  // 수량만큼만 성공
}
```

## 빌드 및 실행

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# 특정 테스트
./gradlew test --tests "*ConcurrencyTest"

# 실행
./gradlew bootRun

# Docker (MySQL, Redis)
docker-compose up -d
```

## 주의사항

1. **self-invocation**: 같은 클래스 내 `@Transactional` 메서드 호출 시 프록시 우회됨
2. **멱등성**: 결제는 `idempotencyKey`로 중복 방지
3. **Outbox**: 이벤트 발행 실패 시 재시도 (최대 3회)
4. **Redis 장애**: 쿠폰 발급 실패 → 정합성 배치로 복구
