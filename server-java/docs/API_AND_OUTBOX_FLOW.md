# API 분리 및 Outbox 이벤트 흐름

## API 분리 상태

### ✅ 완전히 분리됨

```
OrderController
    └─ OrderService (주문 생성만)

PaymentController
    └─ PaymentService (결제 실행만)
```

**API 엔드포인트:**
```
POST /api/orders      (주문 생성)
POST /api/payments    (결제 실행)
```

---

## 전체 흐름

### 1. 주문 생성 (POST /api/orders)

```
[Client]
    ↓
    POST /api/orders
    {
      "userId": "uuid",
      "items": [...]
    }
    ↓
[OrderController]
    ↓
[OrderService.createOrder()]
    @Transactional 시작
    ↓
    ├─ 1. 재고 차감 (ProductPort)
    ├─ 2. Order 저장 (status: PENDING)
    ├─ 3. Outbox 저장
    │      eventType: "ORDER_CREATED"
    │      aggregateId: orderId
    │      payload: {orderId, userId, totalAmount, status}
    ↓
    커밋 (Order + Outbox 함께)
    ↓
[Response]
    {
      "orderId": "uuid",
      "status": "PENDING",
      "totalAmount": 20000
    }
```

**데이터베이스 상태:**
```sql
-- orders 테이블
INSERT INTO orders (id, user_id, status, total_amount)
VALUES ('order-uuid', 'user-uuid', 'PENDING', 20000);

-- outbox 테이블
INSERT INTO outbox (id, event_type, aggregate_id, payload, status)
VALUES ('outbox-uuid-1', 'ORDER_CREATED', 'order-uuid', '{...}', 'PENDING');
```

---

### 2. 결제 실행 (POST /api/payments)

```
[Client]
    ↓
    POST /api/payments
    {
      "orderId": "uuid",
      "userId": "uuid"
    }
    ↓
[PaymentController]
    ↓
[PaymentService.executePayment()]
    @Transactional 시작
    ↓
    ├─ 1. Order 조회 및 검증 (status: PENDING)
    ├─ 2. 결제 중복 체크
    ├─ 3. userId 검증
    ├─ 4. 포인트 사용 (PointPort)
    ├─ 5. Payment 저장 (status: SUCCESS)
    ├─ 6. Order 상태 변경 (PENDING → PAID)
    ├─ 7. Outbox 저장
    │      eventType: "PAYMENT_COMPLETED"
    │      aggregateId: paymentId
    │      payload: {paymentId, orderId, userId, amount, orderStatus}
    ↓
    커밋 (Payment + Order + Outbox 함께)
    ↓
[Response]
    {
      "paymentId": "uuid",
      "orderId": "uuid",
      "status": "SUCCESS",
      "amount": 20000
    }
```

**데이터베이스 상태:**
```sql
-- payments 테이블
INSERT INTO payments (id, order_id, user_id, amount, status)
VALUES ('payment-uuid', 'order-uuid', 'user-uuid', 20000, 'SUCCESS');

-- orders 테이블 (업데이트)
UPDATE orders 
SET status = 'PAID', paid_amount = 20000, updated_at = NOW()
WHERE id = 'order-uuid';

-- outbox 테이블
INSERT INTO outbox (id, event_type, aggregate_id, payload, status)
VALUES ('outbox-uuid-2', 'PAYMENT_COMPLETED', 'payment-uuid', '{...}', 'PENDING');
```

---

### 3. Outbox 이벤트 발행 (자동, 5초마다)

```
[OutboxScheduler]
    @Scheduled(fixedDelay = 5000)
    ↓
    OutboxScheduler.publishPendingEvents()
    @Transactional 시작
    ↓
    ├─ 1. Outbox 조회 (status: PENDING, retryCount < 3)
    │      → 2개 조회: ORDER_CREATED, PAYMENT_COMPLETED
    ↓
    ├─ 2. 첫 번째 이벤트 발행
    │      MockMessageProducer.send("ORDER_CREATED", payload)
    │      → Outbox 상태 변경 (PUBLISHED)
    ↓
    ├─ 3. 두 번째 이벤트 발행
    │      MockMessageProducer.send("PAYMENT_COMPLETED", payload)
    │      → Outbox 상태 변경 (PUBLISHED)
    ↓
    커밋
```

**데이터베이스 상태:**
```sql
-- outbox 테이블 (업데이트)
UPDATE outbox 
SET status = 'PUBLISHED', published_at = NOW()
WHERE id = 'outbox-uuid-1';

UPDATE outbox 
SET status = 'PUBLISHED', published_at = NOW()
WHERE id = 'outbox-uuid-2';
```

**MockMessageProducer 메모리:**
```java
sentMessages = [
    {
        eventType: "ORDER_CREATED",
        payload: "{orderId: ..., userId: ..., totalAmount: 20000, status: PENDING}",
        sentAt: "2025-12-13T14:00:05"
    },
    {
        eventType: "PAYMENT_COMPLETED",
        payload: "{paymentId: ..., orderId: ..., amount: 20000, orderStatus: PAID}",
        sentAt: "2025-12-13T14:00:05"
    }
]
```

---

## 각 단계별 Outbox 상태

### 주문 생성 시

| 시점 | Order 상태 | Payment 존재 | Outbox 이벤트 |
|------|-----------|-------------|--------------|
| 생성 전 | - | ❌ | - |
| 생성 후 | PENDING | ❌ | ORDER_CREATED (PENDING) |

### 결제 실행 시

| 시점 | Order 상태 | Payment 상태 | Outbox 이벤트 |
|------|-----------|-------------|--------------|
| 실행 전 | PENDING | - | ORDER_CREATED (PENDING) |
| 실행 후 | PAID | SUCCESS | ORDER_CREATED (PENDING)<br>PAYMENT_COMPLETED (PENDING) |

### Outbox 발행 시

| 시점 | Outbox 상태 | MockMessageProducer |
|------|------------|---------------------|
| 발행 전 | ORDER_CREATED (PENDING)<br>PAYMENT_COMPLETED (PENDING) | [] |
| 발행 후 | ORDER_CREATED (PUBLISHED)<br>PAYMENT_COMPLETED (PUBLISHED) | [ORDER_CREATED, PAYMENT_COMPLETED] |

---

## 트랜잭션 경계

### 트랜잭션 1: 주문 생성
```
@Transactional
OrderService.createOrder()
    ├─ Order INSERT
    └─ Outbox INSERT (ORDER_CREATED)
```
→ 원자성 보장: Order 실패 시 Outbox도 롤백

### 트랜잭션 2: 결제 실행
```
@Transactional
PaymentService.executePayment()
    ├─ Payment INSERT
    ├─ Order UPDATE (PAID)
    └─ Outbox INSERT (PAYMENT_COMPLETED)
```
→ 원자성 보장: Payment 실패 시 Order, Outbox도 롤백

### 트랜잭션 3: Outbox 발행
```
@Transactional
OutboxScheduler.publishPendingEvents()
    ├─ Outbox SELECT (PENDING)
    ├─ MockMessageProducer.send() (메모리)
    └─ Outbox UPDATE (PUBLISHED)
```
→ 발행 실패 시 재시도 (최대 3회)

---

## 정합성 보장

### 시나리오 1: Order 저장 실패
```
Order INSERT 실패
    ↓
Outbox도 롤백 ✅
    ↓
이벤트 발행 안 됨 ✅
```

### 시나리오 2: Payment 저장 성공, Order 업데이트 실패
```
Payment INSERT 성공
    ↓
Order UPDATE 실패
    ↓
전체 트랜잭션 롤백 ✅
    ↓
Payment, Outbox 모두 롤백 ✅
```

### 시나리오 3: Outbox 발행 실패
```
Outbox 조회 성공
    ↓
MockMessageProducer.send() 실패
    ↓
재시도 카운트 증가 ✅
    ↓
5초 후 재시도 ✅
    ↓
(최대 3회 재시도)
```

---

## API 호출 예시

### 1. 주문 생성

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "660e8400-e29b-41d4-a716-446655440000",
        "quantity": 2
      }
    ]
  }'
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

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "770e8400-e29b-41d4-a716-446655440000",
    "userId": "550e8400-e29b-41d4-a716-446655440000"
  }'
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

## 데이터베이스 조회

### Outbox 이벤트 확인

```sql
-- 모든 Outbox 이벤트 조회
SELECT 
    event_type,
    aggregate_id,
    status,
    created_at,
    published_at
FROM outbox
ORDER BY created_at DESC;
```

**결과:**
```
event_type          | aggregate_id | status    | created_at          | published_at
--------------------|--------------|-----------|---------------------|---------------------
PAYMENT_COMPLETED   | payment-uuid | PUBLISHED | 2025-12-13 14:01:00 | 2025-12-13 14:01:05
ORDER_CREATED       | order-uuid   | PUBLISHED | 2025-12-13 14:00:00 | 2025-12-13 14:00:05
```

### 주문 상태 확인

```sql
-- 주문 상태 조회
SELECT 
    id,
    user_id,
    status,
    total_amount,
    paid_amount,
    created_at,
    updated_at
FROM orders
WHERE id = 'order-uuid';
```

**결과:**
```
id         | user_id   | status | total_amount | paid_amount | created_at          | updated_at
-----------|-----------|--------|--------------|-------------|---------------------|---------------------
order-uuid | user-uuid | PAID   | 20000        | 20000       | 2025-12-13 14:00:00 | 2025-12-13 14:01:00
```

### 결제 정보 확인

```sql
-- 결제 정보 조회
SELECT 
    id,
    order_id,
    user_id,
    amount,
    status,
    created_at
FROM payments
WHERE order_id = 'order-uuid';
```

**결과:**
```
id           | order_id   | user_id   | amount | status  | created_at
-------------|------------|-----------|--------|---------|---------------------
payment-uuid | order-uuid | user-uuid | 20000  | SUCCESS | 2025-12-13 14:01:00
```

---

## 요약

### ✅ API 분리
- OrderController: 주문 생성만
- PaymentController: 결제 실행만
- 완전히 독립적

### ✅ Outbox 이벤트
1. **주문 생성 시**: ORDER_CREATED
2. **결제 실행 시**: PAYMENT_COMPLETED
3. **주문 완료 시**: 결제 실행과 동시에 Order 상태 PAID로 변경
4. **Outbox 발행**: 5초마다 자동 발행

### ✅ 트랜잭션 정합성
- Order + Outbox: 같은 트랜잭션
- Payment + Order + Outbox: 같은 트랜잭션
- 실패 시 전체 롤백

### ✅ 재시도 메커니즘
- 발행 실패 시 재시도 (최대 3회)
- 재시도 초과 시 FAILED 상태
