# 동시성 제어 보고서

## 1. 문제 상황 (AS-IS)

### 1.1 재고 Oversell

```java
// 문제 코드
public void decreaseStock(UUID productId, int quantity) {
    Product p = productRepo.findById(productId);  // 락 없음
    if (p.getStock() >= quantity) {
        p.setStock(p.getStock() - quantity);      // Race Condition
    }
}
```

| 시간 | Thread A | Thread B | 재고 |
|------|----------|----------|------|
| T1 | 재고 조회 (1) | - | 1 |
| T2 | - | 재고 조회 (1) | 1 |
| T3 | 재고 >= 1 ✓ | 재고 >= 1 ✓ | 1 |
| T4 | 재고 = 0 | 재고 = 0 | 0 → -1 |

**결과:** 재고 1개인데 2명이 구매 성공 → Oversell

---

### 1.2 포인트 잔액 음수

```java
// 문제 코드
public void usePoint(UUID userId, Long amount) {
    Long balance = getBalance(userId);  // 락 없음
    if (balance >= amount) {
        deduct(userId, amount);         // Race Condition
    }
}
```

| 시간 | Thread A | Thread B | 잔액 |
|------|----------|----------|------|
| T1 | 잔액 조회 (10000) | - | 10000 |
| T2 | - | 잔액 조회 (10000) | 10000 |
| T3 | 10000 >= 8000 ✓ | 10000 >= 8000 ✓ | 10000 |
| T4 | 잔액 = 2000 | 잔액 = -6000 | -6000 |

**결과:** 잔액 10000원인데 16000원 사용 → 음수 잔액

---

### 1.3 쿠폰 초과/중복 발급

```java
// 문제 코드
public void issueCoupon(UUID couponId, UUID userId) {
    Coupon c = couponRepo.findById(couponId);  // 락 없음
    if (c.getRemain() > 0) {
        c.setRemain(c.getRemain() - 1);
        userCouponRepo.save(new UserCoupon(userId, c));  // 중복 가능
    }
}
```

**문제:**
1. 수량 100개인데 105명에게 발급 → 초과 발급
2. 같은 유저가 2번 발급받음 → 중복 발급

---

## 2. 해결 전략 (TO-BE)

### 2.1 재고: 비관적 락 + 데드락 방지

```java
// ProductJpaRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM ProductEntity p WHERE p.id = :id")
Optional<ProductEntity> findByIdWithLock(@Param("id") UUID id);
```

```java
// OrderService - 데드락 방지 (productId 정렬)
List<OrderItemRequest> sortedRequests = itemRequests.stream()
    .sorted(Comparator.comparing(OrderItemRequest::productId))
    .toList();

for (OrderItemRequest request : sortedRequests) {
    ProductSnapshot snapshot = productPort.decreaseStockWithSnapshot(
        request.productId(), request.quantity()
    );
}
```

**핵심:**
- `SELECT ... FOR UPDATE`로 행 락 획득
- productId 정렬로 데드락 방지 (ADR-021)

---

### 2.2 포인트: 비관적 락 + 잔액 검증

```java
// PointRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<PointEntity> findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(UUID userId, LocalDateTime now);
```

```java
// PointService
@Transactional
public void usePoint(UUID userId, Long amount) {
    // 1. 락 획득 + 조회
    var points = pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(userId, now);
    
    // 2. 잔액 검증
    Long totalBalance = points.stream().mapToLong(PointEntity::getAmount).sum();
    if (totalBalance < amount) {
        throw new IllegalArgumentException("Insufficient point balance");
    }
    
    // 3. 만료일 순 차감
    Long remaining = amount;
    for (PointEntity point : points) {
        if (remaining <= 0) break;
        Long useAmount = Math.min(point.getAmount(), remaining);
        point.use(useAmount);
        remaining -= useAmount;
    }
}
```

**핵심:**
- 락 획득 후 잔액 검증 → 음수 방지
- 만료일 순 차감 (FIFO)

---

### 2.3 쿠폰: 비관적 락 + UNIQUE 제약

```java
// CouponJpaRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM CouponEntity c WHERE c.id = :id")
Optional<CouponEntity> findByIdWithLock(@Param("id") UUID id);
```

```java
// CouponService - 3중 방어
@Transactional
public UserCoupon issueCoupon(UUID couponId, UUID userId) {
    // 1. 빠른 실패 (락 전 체크)
    if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
        throw new IllegalStateException("이미 발급받은 쿠폰입니다");
    }

    // 2. 비관적 락으로 수량 차감
    Coupon coupon = couponRepository.findByIdWithLock(couponId)
        .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));
    coupon.issue();  // 수량 검증 + 차감

    // 3. UNIQUE constraint로 중복 방지
    try {
        return userCouponRepository.save(new UserCoupon(userId, coupon));
    } catch (DataIntegrityViolationException e) {
        throw new IllegalStateException("이미 발급받은 쿠폰입니다");
    }
}
```

**핵심:**
- 빠른 실패: 불필요한 락 획득 방지
- 비관적 락: 수량 초과 방지
- UNIQUE 제약: 중복 발급 최종 방어

---

### 2.4 결제: 주문 락 + 멱등성 키

```java
// PaymentService
@Transactional
public Payment executePayment(UUID orderId, UUID userId, String idempotencyKey) {
    // 1. 멱등성 키로 중복 요청 처리
    Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existingByKey.isPresent()) {
        return existingByKey.get();
    }

    // 2. 주문 락 획득
    Order order = orderRepository.findByIdWithLock(orderId)
        .orElseThrow(() -> new IllegalArgumentException("Order not found"));

    // 3. orderId로 중복 결제 체크
    if (paymentRepository.findByOrderId(orderId).isPresent()) {
        throw new IllegalStateException("Payment already exists");
    }

    // 4. 포인트 사용 + 결제 생성
    pointPort.usePoint(userId, order.getFinalAmount());
    Payment payment = new Payment(orderId, userId, idempotencyKey, ...);
    return paymentRepository.save(payment);
}
```

**핵심:**
- 멱등성 키: 네트워크 재시도 안전
- 주문 락: 동시 결제 방지

---

## 3. 테스트 결과

### 3.1 재고 동시성 테스트

```java
@Test
void shouldHandleConcurrentOrdersWithPessimisticLock() throws InterruptedException {
    // given: 재고 10개
    ProductEntity product = productService.createProduct("Product A", "Desc", BigDecimal.valueOf(10000), 10);
    
    int threadCount = 10;
    ExecutorService es = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 10스레드 동시 주문
    for (int i = 0; i < threadCount; i++) {
        es.submit(() -> {
            try {
                orderService.createOrder(UUID.randomUUID(), List.of(new OrderItemRequest(product.getId(), 1)));
                successCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await();

    // then: 10개 모두 성공, 재고 0
    assertThat(successCount.get()).isEqualTo(10);
    assertThat(productService.getProduct(product.getId()).getStock()).isEqualTo(0);
}
```

**결과:** ✅ PASS - 재고 정확히 0

---

### 3.2 재고 부족 테스트

```java
@Test
void shouldFailWhenInsufficientStock() throws InterruptedException {
    // given: 재고 5개
    ProductEntity product = productService.createProduct("Product A", "Desc", BigDecimal.valueOf(10000), 5);
    
    int threadCount = 10;
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // when: 10스레드 동시 주문
    // ...

    // then: 5개 성공, 5개 실패
    assertThat(successCount.get()).isEqualTo(5);
    assertThat(failCount.get()).isEqualTo(5);
    assertThat(productService.getProduct(product.getId()).getStock()).isEqualTo(0);
}
```

**결과:** ✅ PASS - 5성공, 5실패, 재고 0

---

### 3.3 데드락 방지 테스트

```java
@Test
void shouldPreventDeadlockByOrderingProductIds() throws InterruptedException {
    // given: 상품 A, B 각각 재고 10개
    ProductEntity productA = productService.createProduct("A", "Desc", BigDecimal.valueOf(10000), 10);
    ProductEntity productB = productService.createProduct("B", "Desc", BigDecimal.valueOf(20000), 10);

    int threadCount = 5;
    AtomicInteger successCount = new AtomicInteger(0);

    // when: [A,B] 순서와 [B,A] 순서 동시 실행
    for (int i = 0; i < threadCount; i++) {
        // 주문1: A → B
        es.submit(() -> orderService.createOrder(userId, List.of(itemA, itemB)));
        // 주문2: B → A (역순)
        es.submit(() -> orderService.createOrder(userId, List.of(itemB, itemA)));
    }

    // then: 데드락 없이 모두 성공
    assertThat(successCount.get()).isEqualTo(threadCount * 2);
}
```

**결과:** ✅ PASS - 데드락 없음 (productId 정렬로 방지)

---

### 3.4 중복 결제 방지 테스트

```java
@Test
void shouldPreventDuplicatePayment() throws InterruptedException {
    // given: 주문 생성, 포인트 충전
    Order order = orderService.createOrder(userId, items);
    pointService.chargePoint(userId, 100000L);

    int threadCount = 5;
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // when: 5스레드 동시 결제
    for (int i = 0; i < threadCount; i++) {
        es.submit(() -> {
            try {
                paymentService.executePayment(order.getId(), userId, UUID.randomUUID().toString());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        });
    }

    // then: 1개만 성공
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(4);
}
```

**결과:** ✅ PASS - 1성공, 4실패

---

## 4. 설계 결정 (ADR 참조)

| ADR | 결정 | 이유 |
|-----|------|------|
| ADR-018 | 재고: 비관적 락 | 충돌 빈도 높음, 재시도 비용 > 락 대기 비용 |
| ADR-019 | 쿠폰: 비관적 락 + UNIQUE | 선착순 특성상 충돌 빈도 높음 |
| ADR-020 | 포인트: 비관적 락 | 금액 정확성 필수 |
| ADR-021 | 데드락 방지: 락 순서 일관성 | productId 정렬로 교차 락 방지 |

---

## 5. 격리 수준 설정

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        connection:
          isolation: 2  # READ_COMMITTED
```

**선택 이유:**
- 대부분의 RDBMS 기본값 (PostgreSQL, Oracle, SQL Server)
- 비관적 락과 조합 시 충분한 정합성 보장
- RR 대비 undo log 부담 감소 → 대용량 트래픽에 유리

---

## 6. 요약

| 도메인 | 동시성 이슈 | 해결 전략 | 테스트 결과 |
|--------|------------|----------|------------|
| 재고 | Oversell | 비관적 락 + 정렬 | ✅ |
| 포인트 | 음수 잔액 | 비관적 락 + 검증 | ✅ |
| 쿠폰 | 초과/중복 발급 | 비관적 락 + UNIQUE | ✅ |
| 결제 | 중복 결제 | 주문 락 + 멱등성 키 | ✅ |
