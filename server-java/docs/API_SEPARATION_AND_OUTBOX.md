# API 분리 및 Outbox 패턴 구현 완료

## 변경 사항 요약

### 1. API 분리
- OrderController와 PaymentController 독립
- 각 Controller가 자신의 Service만 의존
- RESTful API 설계 개선

### 2. Outbox 패턴 적용
- 트랜잭션 정합성 보장
- 이벤트 기반 아키텍처
- MockMessageProducer로 테스트 가능

### 3. 트랜잭션 경계 명확화
- PaymentService에서 Order 직접 수정 제거
- 이벤트 기반 최종 일관성

---

## 변경 전/후 비교

### 변경 전

```
OrderController
    ├─ OrderService (주문 생성)
    └─ PaymentService (결제 실행) ❌ 결합도 높음

PaymentService
    ├─ Payment 생성
    └─ Order 상태 직접 변경 ❌ 강한 결합
```

**API:**
```
POST /api/orders
POST /api/orders/{orderId}/payment  ❌ Payment가 Order 하위 리소스
```

### 변경 후

```
OrderController → OrderService
PaymentController → PaymentService ✅ 독립

OrderService
    ├─ Order 저장
    └─ Outbox 저장 (ORDER_CREATED)

PaymentService
    ├─ Payment 저장
    └─ Outbox 저장 (PAYMENT_COMPLETED) ✅ 이벤트 기반
```

**API:**
```
POST /api/orders      ✅ 주문 생성
POST /api/payments    ✅ 결제 실행 (독립적)
```

---

## 구현된 컴포넌트

### 1. Outbox 도메인

```
domain/outbox/
├── Outbox.java              # 도메인 모델
├── OutboxStatus.java        # PENDING, PUBLISHED, FAILED
└── OutboxRepository.java    # 리포지토리 인터페이스
```

**Outbox 모델:**
```java
public class Outbox {
    private UUID id;
    private String eventType;        // ORDER_CREATED, PAYMENT_COMPLETED
    private UUID aggregateId;        // Order ID, Payment ID
    private String payload;          // JSON
    private OutboxStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private Integer retryCount;
}
```

### 2. Infrastructure Layer

```
infrastructure/outbox/
├── persistence/
│   ├── OutboxEntity.java
│   ├── OutboxJpaRepository.java
│   └── OutboxRepositoryImpl.java
├── message/
│   └── MockMessageProducer.java
├── scheduler/
│   └── OutboxScheduler.java
└── config/
    └── OutboxConfig.java
```

### 3. Application Layer

```
application/outbox/
└── MessageProducer.java     # Port 인터페이스
```

### 4. Presentation Layer

```
presentation/payment/
└── PaymentController.java   # 신규 생성
```

---

## API 명세

### 1. 주문 생성

```http
POST /api/orders
Content-Type: application/json

{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "productId": "660e8400-e29b-41d4-a716-446655440000",
      "quantity": 2
    }
  ]
}
```

**Response:**
```json
{
  "orderId": "770e8400-e29b-41d4-a716-446655440000",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "totalAmount": 20000,
  "createdAt": "2025-12-13T14:00:00"
}
```

### 2. 결제 실행

```http
POST /api/payments
Content-Type: application/json

{
  "orderId": "770e8400-e29b-41d4-a716-446655440000",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
```json
{
  "paymentId": "880e8400-e29b-41d4-a716-446655440000",
  "orderId": "770e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "amount": 20000,
  "createdAt": "2025-12-13T14:01:00"
}
```

---

## 트랜잭션 흐름

### 주문 생성 트랜잭션

```
@Transactional
OrderService.createOrder()
    ↓
    ├─ 1. 재고 차감 (ProductPort)
    ├─ 2. Order 저장
    ├─ 3. Outbox 저장 (ORDER_CREATED)
    ↓
    커밋 (Order + Outbox 함께)
```

### 결제 실행 트랜잭션

```
@Transactional
PaymentService.executePayment()
    ↓
    ├─ 1. Order 조회 및 검증
    ├─ 2. 포인트 사용 (PointPort)
    ├─ 3. Payment 저장
    ├─ 4. Outbox 저장 (PAYMENT_COMPLETED)
    ↓
    커밋 (Payment + Outbox 함께)
```

### Outbox 발행 (별도 스레드)

```
@Scheduled(fixedDelay = 5000)
OutboxScheduler.publishPendingEvents()
    ↓
    ├─ 1. PENDING 상태 Outbox 조회
    ├─ 2. MockMessageProducer.send()
    ├─ 3. PUBLISHED로 업데이트
    ↓
    (재시도 3회까지)
```

---

## 트랜잭션 정합성 보장

### 시나리오 1: Order 저장 실패

```
Order 저장 실패
    ↓
Outbox도 롤백 ✅
    ↓
이벤트 발행 안 됨 ✅
```

### 시나리오 2: Payment 저장 성공, Outbox 저장 실패

```
Payment 저장 성공
    ↓
Outbox 저장 실패
    ↓
전체 트랜잭션 롤백 ✅
    ↓
Payment도 롤백됨 ✅
```

### 시나리오 3: Outbox 발행 실패

```
Payment 저장 성공
Outbox 저장 성공 (PENDING)
    ↓
OutboxScheduler 발행 시도
    ↓
발행 실패
    ↓
재시도 카운트 증가 ✅
    ↓
5초 후 재시도 ✅
    ↓
(최대 3회 재시도)
```

---

## userId 기반 종속 설계

### Payment 도메인

```java
public class Payment {
    private UUID orderId;   // Order 참조
    private UUID userId;    // 반정규화 (성능 최적화)
}
```

### 데이터베이스 인덱스

```sql
CREATE TABLE payments (
    id BINARY(16) PRIMARY KEY,
    order_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,  -- 반정규화
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    
    INDEX idx_order_id (order_id),
    INDEX idx_user_id_created_at (user_id, created_at)  -- 사용자별 조회 최적화
);
```

### 보안 검증

```java
// PaymentService에서 userId 검증
if (!order.getUserId().equals(userId)) {
    throw new IllegalArgumentException("User mismatch");
}
```

---

## 테스트 방법

### 1. 주문 생성 테스트

```java
@Test
void createOrder() {
    // Given
    UUID userId = UUID.randomUUID();
    List<OrderItemRequest> items = List.of(...);
    
    // When
    Order order = orderService.createOrder(userId, items);
    
    // Then
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    
    // Outbox 저장 확인
    List<Outbox> outboxes = outboxRepository
        .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);
    assertThat(outboxes).hasSize(1);
    assertThat(outboxes.get(0).getEventType()).isEqualTo("ORDER_CREATED");
}
```

### 2. 결제 실행 테스트

```java
@Test
void executePayment() {
    // Given
    Order order = createTestOrder();
    
    // When
    Payment payment = paymentService.executePayment(order.getId(), order.getUserId());
    
    // Then
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    
    // Outbox 저장 확인
    List<Outbox> outboxes = outboxRepository
        .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);
    assertThat(outboxes)
        .anyMatch(o -> o.getEventType().equals("PAYMENT_COMPLETED"));
}
```

### 3. Outbox 발행 테스트

```java
@Test
void publishPendingEvents() {
    // Given
    Order order = createTestOrder();
    Payment payment = executeTestPayment(order.getId());
    
    // When
    outboxScheduler.publishPendingEvents();
    
    // Then
    List<MockMessageProducer.Message> messages = mockMessageProducer.getSentMessages();
    assertThat(messages).hasSize(2);  // ORDER_CREATED, PAYMENT_COMPLETED
    
    // Outbox 상태 확인
    List<Outbox> published = outboxRepository
        .findByStatusAndRetryCountLessThan(OutboxStatus.PUBLISHED, 3);
    assertThat(published).hasSize(2);
}
```

---

## 쿼리 로그 수집 실험 가능

### 1. Outbox INSERT 성능 측정

```sql
SELECT 
    AVG(execution_time_ms) as avg_time,
    COUNT(*) as count
FROM query_log
WHERE sql LIKE '%INSERT INTO outbox%'
  AND executed_at > NOW() - INTERVAL 1 HOUR;
```

### 2. Outbox 조회 성능 측정

```sql
SELECT 
    AVG(execution_time_ms) as avg_time
FROM query_log
WHERE sql LIKE '%SELECT%outbox%status%'
  AND executed_at > NOW() - INTERVAL 1 HOUR;
```

### 3. 파티셔닝 효과 측정

```sql
-- Outbox 테이블 파티셔닝
ALTER TABLE outbox 
PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p_2025_12_13 VALUES LESS THAN (TO_DAYS('2025-12-14')),
    PARTITION p_2025_12_14 VALUES LESS THAN (TO_DAYS('2025-12-15'))
);

-- 파티션 프루닝 효과 확인
EXPLAIN SELECT * FROM outbox 
WHERE created_at BETWEEN '2025-12-13' AND '2025-12-14';
```

---

## 다음 단계

### Phase 2: 이벤트 기반 Order 상태 변경 (선택)

현재는 PaymentService에서 Order 상태를 직접 변경하지 않습니다.
필요 시 Application Event로 Order 상태 변경 가능:

```java
// PaymentService
eventPublisher.publishEvent(new PaymentCompletedEvent(payment));

// OrderEventListener
@EventListener
@Async
@Transactional
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    Order order = orderRepository.findById(event.getOrderId());
    order.completePayment(event.getAmount());
    orderRepository.save(order);
}
```

### Phase 3: 쿼리 로그 수집 구현

1. QueryCollectorInterceptor
2. QueryLogCollector
3. QueryBatchProcessor
4. 파티셔닝 적용

### Phase 4: userId 기반 조회 API

```
GET /api/users/{userId}/orders
GET /api/users/{userId}/payments
```

---

## 장점 요약

### 1. API 분리
- ✅ OrderController와 PaymentController 독립
- ✅ RESTful API 설계 개선
- ✅ 마이크로서비스 전환 용이

### 2. Outbox 패턴
- ✅ 트랜잭션 정합성 100% 보장
- ✅ At-Least-Once 전송 보장
- ✅ 재시도 메커니즘

### 3. 이벤트 기반
- ✅ 느슨한 결합
- ✅ 확장성 높음
- ✅ 최종 일관성

### 4. 쿼리 로그 수집 실험
- ✅ Outbox INSERT 성능 측정
- ✅ 파티셔닝 효과 검증
- ✅ 인덱스 최적화 실험

### 5. 학습 가치
- ✅ Outbox 패턴 완벽 이해
- ✅ 트랜잭션 경계 설정
- ✅ 이벤트 기반 아키텍처
- ✅ 실무 활용 가능

---

## 참고 자료

- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [QUERY_LOG_AND_OUTBOX.md](./QUERY_LOG_AND_OUTBOX.md)
- [TESTCONTAINERS_SUMMARY.md](./TESTCONTAINERS_SUMMARY.md)
