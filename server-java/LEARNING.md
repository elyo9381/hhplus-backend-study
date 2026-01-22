# Task Learning

프로젝트 개발 과정에서 학습한 패턴과 안티패턴을 기록합니다.

---

## 패턴 (성공 사례)

### 동시성 제어

| ID | 패턴 | 코드 예시 | 적용 상황 |
|----|------|----------|----------|
| P001 | 비관적 락 + ID 정렬 | `sortedRequests.stream().sorted(comparing(::productId))` | 재고 차감 시 데드락 방지 |
| P002 | Redisson 분산락 | `lock.tryLock(5, 10, SECONDS)` | 선착순 쿠폰 발급 |
| P003 | TransactionSynchronization | `afterCompletion(STATUS_ROLLED_BACK)` | Redis-DB 정합성 |
| P004 | 멱등성 키 | `findByIdempotencyKey(key)` | 중복 결제 방지 |

### 도메인 모델

| ID | 패턴 | 코드 예시 | 적용 상황 |
|----|------|----------|----------|
| P005 | Rich Domain Model | `order.completePayment(amount)` | 비즈니스 로직 응집 |
| P006 | 생성자 2개 패턴 | 신규 생성용 + Entity 변환용 | 도메인 객체 생성 |
| P007 | Port 인터페이스 | `ProductPort`, `PointPort` | 도메인 간 의존성 분리 |
| P008 | ID 참조 | `Order.userId`, `Payment.orderId` | Aggregate 간 느슨한 결합 |

### 이벤트/캐시

| ID | 패턴 | 코드 예시 | 적용 상황 |
|----|------|----------|----------|
| P009 | Outbox 패턴 | 트랜잭션 내 Outbox 저장 → 스케줄러 발행 | 이벤트 발행 보장 |
| P010 | Write-Through | DB + Redis 동시 저장 | 쿠폰 생성 |
| P011 | Redis as SoT | Redis 우선, DB 동기화 | 선착순 쿠폰 발급 |
| P012 | 비핵심 기능 분리 | `try-catch`로 랭킹 실패 무시 | 결제 성공 보장 |

### 테스트

| ID | 패턴 | 코드 예시 | 적용 상황 |
|----|------|----------|----------|
| P013 | 동시성 테스트 | `ExecutorService` + `CountDownLatch` | 락 검증 |
| P014 | Testcontainers | `@DynamicPropertySource` | 통합 테스트 환경 |
| P015 | 한글 테스트명 | `선착순_쿠폰_발급_수량만큼만_성공()` | 가독성 향상 |

---

## 안티패턴 (실수 사례)

### 동시성

| ID | 안티패턴 | 올바른 방법 | 이유 |
|----|---------|------------|------|
| A001 | `synchronized` 사용 | 비관적 락 또는 분산락 | 분산 환경 미지원 |
| A002 | 락 순서 불일치 | ID 정렬 후 락 획득 | 데드락 발생 |
| A003 | 낙관적 락 + 높은 경합 | 비관적 락 사용 | 재시도 폭증 |
| A004 | Redis 먼저 → DB (롤백 미처리) | TransactionSynchronization | 정합성 깨짐 |

### 트랜잭션

| ID | 안티패턴 | 올바른 방법 | 이유 |
|----|---------|------------|------|
| A005 | Controller에 `@Transactional` | Service에만 적용 | HTTP 커넥션 점유 |
| A006 | self-invocation | 별도 클래스 분리 | 프록시 우회로 트랜잭션 미적용 |
| A007 | 긴 트랜잭션 | 트랜잭션 범위 최소화 | 락 점유 시간 증가 |

### DDD

| ID | 안티패턴 | 올바른 방법 | 이유 |
|----|---------|------------|------|
| A008 | 도메인에서 Repository 호출 | Service에서 조회 후 전달 | 레이어 의존성 위반 |
| A009 | setter로 상태 변경 | 의미 있는 메서드 (`complete()`) | 비즈니스 의도 불명확 |
| A010 | Entity를 Controller에 노출 | Response DTO 변환 | 레이어 분리 |

### 기타

| ID | 안티패턴 | 올바른 방법 | 이유 |
|----|---------|------------|------|
| A011 | 예외 무시 (`catch` 빈 블록) | 로깅 또는 재throw | 디버깅 어려움 |
| A012 | 하드코딩된 설정값 | `application.yml` | 환경별 설정 불가 |

---

## 동시성 제어 가이드

| 상황 | 전략 | 구현 | 이유 |
|------|------|------|------|
| 재고 차감 | 비관적 락 | `@Lock(PESSIMISTIC_WRITE)` | 높은 경합, 정확성 필수 |
| 포인트 사용 | 비관적 락 | `findByUserIdWithLock()` | 잔액 정확성 필수 |
| 주문 결제 | 비관적 락 | `findByIdWithLock()` | 중복 결제 방지 |
| 선착순 쿠폰 | Redisson 분산락 | `RLock.tryLock()` | 높은 TPS 필요 |
| 인기상품 랭킹 | Redis Sorted Set | `ZINCRBY` | 실시간 집계 |

---

## Redis 캐시 전략 가이드

| 용도 | 전략 | 키 패턴 | TTL |
|------|------|---------|-----|
| 쿠폰 정보 | Write-Through | `coupon:info:{id}` | 만료일+1일 |
| 쿠폰 발급 | Redis as SoT | `coupon:issued:{id}` | 만료일+1일 |
| 분산락 | Redisson Lock | `coupon:lock:{id}` | 10초 (Watchdog) |
| 일별 랭킹 | Redis Only | `product:ranking:daily:{date}` | 3일 |
| 주별 랭킹 | Redis Only | `product:ranking:weekly:{week}` | 10일 |
| 상품 목록 | Cache-Aside | `products::all` | 설정값 |

---

## 트랜잭션 경계 가이드

```java
// ✅ 올바른 예
@Service
public class OrderService {
    @Transactional
    public Order createOrder(UUID userId, List<OrderItemRequest> items) {
        // 1. 재고 차감 (비관적 락)
        // 2. 주문 저장
        // 3. Outbox 저장
        // → 모두 같은 트랜잭션
    }
}

// ❌ 잘못된 예 1: Controller에서 트랜잭션
@RestController
public class OrderController {
    @Transactional  // HTTP 커넥션 점유
    public ResponseEntity<?> createOrder(...) { }
}

// ❌ 잘못된 예 2: self-invocation
@Service
public class OrderService {
    @Transactional
    public void methodA() {
        methodB();  // 트랜잭션 미적용!
    }
    
    @Transactional
    public void methodB() { }
}
```

---

## 테스트 패턴 가이드

### 동시성 테스트
```java
@Test
void 선착순_쿠폰_발급_수량만큼만_성공() throws InterruptedException {
    int threadCount = 20;
    int maxQuantity = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                couponService.issueCoupon(couponId, UUID.randomUUID());
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                failCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await();
    executor.shutdown();
    
    assertThat(successCount.get()).isEqualTo(maxQuantity);
    assertThat(failCount.get()).isEqualTo(threadCount - maxQuantity);
}
```

### Testcontainers 설정
```java
@SpringBootTest
@ActiveProfiles("test")
class SomeIntegrationTest extends TestContainerSupport {
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerSupport::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerSupport::getUsername);
        registry.add("spring.datasource.password", TestContainerSupport::getPassword);
    }
}
```

---

## 피드백 히스토리

| 날짜 | 태스크 | 문제 | 해결 |
|------|--------|------|------|
| 2026-01-21 | 선착순 쿠폰 | DB 비관적 락 TPS 한계 (~50) | Redisson 분산락으로 전환 (TPS 5000+) |
| 2026-01-21 | Redis 정합성 | DB 롤백 시 Redis 불일치 | TransactionSynchronization 적용 |
| 2026-01-21 | 쿠폰 발급 | 매 요청 DB 조회 | Redis Only 패턴 (DB 조회 제거) |
| 2026-01-21 | 데드락 | 여러 상품 동시 주문 시 데드락 | 상품 ID 정렬 후 락 획득 |

---

## 자동 업데이트 규칙

작업 완료 시:
1. 새로운 패턴 발견 → 패턴 섹션에 추가
2. 실수 발생 → 안티패턴 섹션에 추가
3. 피드백 받음 → 히스토리에 기록
4. 중복 항목은 스킵
