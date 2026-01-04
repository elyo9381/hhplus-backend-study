# 동시성 제어 보고서

## 1. 요약

| 도메인 | 문제 | 해결 전략 | 테스트 |
|--------|------|----------|--------|
| 재고 | Oversell | 비관적 락 + productId 정렬 | ✅ |
| 포인트 | 음수 잔액 | 비관적 락 + 잔액 검증 | ✅ |
| 쿠폰 | 초과/중복 발급 | 비관적 락 + UNIQUE 제약 | ✅ |
| 결제 | 중복 결제 | 주문 락 + 멱등성 키 | ✅ |

---

## 2. 문제 상황 (AS-IS)

### 재고 Oversell
```
Thread A: 재고 조회(1) → 검증 통과 → 차감
Thread B: 재고 조회(1) → 검증 통과 → 차감
결과: 재고 -1 (Oversell)
```

### 포인트 음수 잔액
```
Thread A: 잔액 조회(10000) → 8000원 사용
Thread B: 잔액 조회(10000) → 8000원 사용
결과: 잔액 -6000원
```

### 쿠폰 초과/중복 발급
```
Thread A~Z: 동시에 쿠폰 발급 요청
결과: 수량 100개인데 105명 발급 / 같은 유저 2번 발급
```

---

## 3. 해결 전략 (TO-BE)

### 3.1 재고: 비관적 락 + 데드락 방지

| 항목 | 구현 |
|------|------|
| 락 | `@Lock(PESSIMISTIC_WRITE)` → SELECT ... FOR UPDATE |
| 데드락 방지 | productId 정렬로 락 순서 일관성 (ADR-021) |

**코드 참조:**
- [ProductJpaRepository.findByIdWithLock()](../../../src/main/java/kr/hhplus/be/server/infrastructure/product/persistence/ProductJpaRepository.java#L23)
- [OrderService.createOrder()](../../../src/main/java/kr/hhplus/be/server/application/order/OrderService.java#L38)

### 3.2 포인트: 비관적 락 + 잔액 검증

| 항목 | 구현 |
|------|------|
| 락 | `@Lock(PESSIMISTIC_WRITE)` |
| 검증 | 락 획득 후 잔액 확인 → 부족 시 예외 |

**코드 참조:**
- [PointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc()](../../../src/main/java/kr/hhplus/be/server/infrastructure/point/persistence/PointRepository.java#L18)
- [PointService.usePoint()](../../../src/main/java/kr/hhplus/be/server/application/point/PointService.java#L53)

### 3.3 쿠폰: 3중 방어

| 단계 | 구현 | 목적 |
|------|------|------|
| 1 | 중복 체크 (락 전) | 불필요한 락 대기 방지 |
| 2 | 비관적 락 | 수량 초과 방지 |
| 3 | UNIQUE 제약 | 중복 발급 최종 방어 |

**코드 참조:**
- [CouponJpaRepository.findByIdWithLock()](../../../src/main/java/kr/hhplus/be/server/infrastructure/coupon/persistence/CouponJpaRepository.java#L19)
- [CouponService.issueCoupon()](../../../src/main/java/kr/hhplus/be/server/application/coupon/CouponService.java#L30)

---

## 4. 테스트

### 테스트 파일
- [ConcurrencyIntegrationTest](../../../src/test/java/kr/hhplus/be/server/integration/ConcurrencyIntegrationTest.java) - 재고, 결제
- [PointConcurrencyTest](../../../src/test/java/kr/hhplus/be/server/point/PointConcurrencyTest.java) - 포인트
- [CouponConcurrencyTest](../../../src/test/java/kr/hhplus/be/server/coupon/CouponConcurrencyTest.java) - 쿠폰

### 테스트 시나리오

| 테스트 | 시나리오 | 기대 결과 |
|--------|----------|----------|
| 재고 동시 주문 | 재고 10, 10스레드 | 10성공, 재고 0 |
| 재고 부족 | 재고 5, 10스레드 | 5성공, 5실패 |
| 데드락 방지 | [A,B] / [B,A] 교차 | 데드락 없음 |
| 포인트 동시 사용 | 10000원, 10스레드 2000원씩 | 5성공, 5실패 |
| 쿠폰 선착순 | 수량 10, 20스레드 | 10성공, 10실패 |
| 쿠폰 중복 방지 | 같은 유저 10스레드 | 1성공, 9실패 |

---

## 5. 설계 결정 (ADR)

| ADR | 결정 | 이유 |
|-----|------|------|
| [ADR-018](../../../README.md) | 재고: 비관적 락 | 충돌 빈도 높음 |
| [ADR-019](../../../README.md) | 쿠폰: 비관적 락 + UNIQUE | 선착순 특성 |
| [ADR-020](../../../README.md) | 포인트: 비관적 락 | 금액 정확성 필수 |
| [ADR-021](../../../README.md) | 데드락 방지: 락 순서 일관성 | productId 정렬 |

---

## 6. 격리 수준

```yaml
# application.yml
spring.jpa.properties.hibernate.connection.isolation: 2  # READ_COMMITTED
```

**선택 이유:** RC + 비관적 락 조합이 실무 표준 (PostgreSQL, Oracle, SQL Server 기본값)
