# Outbox 패턴 테스트 가이드

## 작성된 테스트

### 1. OutboxBasicTest
- Outbox 저장
- PENDING 상태 조회
- OutboxScheduler 발행
- PUBLISHED 상태 변경

### 2. OutboxTransactionTest
- 주문 생성 성공 시 Outbox 저장
- 주문 생성 실패 시 Outbox 롤백
- 재고 부족 시 전체 롤백
- Outbox payload 검증

### 3. OutboxSchedulerTest
- PENDING 이벤트 발행
- 여러 이벤트 발행
- PUBLISHED 이벤트 재발행 방지
- JSON payload 검증

### 4. PaymentOutboxTest
- 결제 완료 시 PAYMENT_COMPLETED 이벤트
- 전체 플로우 (주문 → 결제 → 발행)
- 결제 실패 시 Outbox 미생성
- orderStatus PAID 검증

### 5. OutboxRetryTest
- 재시도 카운트 조회
- 재시도 카운트 증가
- FAILED 상태 변경
- 에러 메시지 저장

## 수동 테스트 방법

### 1. 주문 생성 및 Outbox 확인

```bash
# 1. 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {"productId": "660e8400-e29b-41d4-a716-446655440000", "quantity": 2}
    ]
  }'

# 2. Outbox 확인
SELECT * FROM outbox WHERE event_type = 'ORDER_CREATED' ORDER BY created_at DESC LIMIT 1;
```

**예상 결과:**
```
event_type: ORDER_CREATED
status: PENDING
retry_count: 0
```

### 2. 결제 실행 및 Outbox 확인

```bash
# 1. 결제 실행
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "770e8400-e29b-41d4-a716-446655440000",
    "userId": "550e8400-e29b-41d4-a716-446655440000"
  }'

# 2. Outbox 확인
SELECT * FROM outbox WHERE event_type = 'PAYMENT_COMPLETED' ORDER BY created_at DESC LIMIT 1;
```

**예상 결과:**
```
event_type: PAYMENT_COMPLETED
status: PENDING
payload: {"paymentId":"...","orderId":"...","orderStatus":"PAID"}
```

### 3. OutboxScheduler 발행 확인

```bash
# 5초 대기 후 Outbox 상태 확인
SELECT event_type, status, published_at FROM outbox ORDER BY created_at DESC;
```

**예상 결과:**
```
event_type          | status    | published_at
--------------------|-----------|---------------------
PAYMENT_COMPLETED   | PUBLISHED | 2025-12-13 14:01:05
ORDER_CREATED       | PUBLISHED | 2025-12-13 14:01:05
```

### 4. MockMessageProducer 로그 확인

```bash
# 애플리케이션 로그 확인
tail -f logs/application.log | grep MockMessageProducer
```

**예상 출력:**
```
[MockMessageProducer] Sent: ORDER_CREATED
[MockMessageProducer] Sent: PAYMENT_COMPLETED
```

## 트랜잭션 정합성 테스트

### 시나리오 1: 주문 생성 실패

```bash
# 존재하지 않는 상품으로 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {"productId": "invalid-product-id", "quantity": 1}
    ]
  }'

# Outbox 확인 (저장되지 않아야 함)
SELECT COUNT(*) FROM outbox WHERE event_type = 'ORDER_CREATED';
```

### 시나리오 2: 결제 실패

```bash
# 잘못된 userId로 결제 시도
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "770e8400-e29b-41d4-a716-446655440000",
    "userId": "wrong-user-id"
  }'

# PAYMENT_COMPLETED Outbox 확인 (저장되지 않아야 함)
SELECT COUNT(*) FROM outbox WHERE event_type = 'PAYMENT_COMPLETED';
```

## 재시도 메커니즘 테스트

### 수동으로 재시도 시뮬레이션

```sql
-- 1. Outbox 생성
INSERT INTO outbox (id, event_type, aggregate_id, payload, status, created_at, retry_count)
VALUES (UUID(), 'TEST_EVENT', UUID(), '{}', 'PENDING', NOW(), 0);

-- 2. 재시도 카운트 증가
UPDATE outbox SET retry_count = retry_count + 1 WHERE event_type = 'TEST_EVENT';

-- 3. 재시도 3회 초과 시 FAILED로 변경
UPDATE outbox SET status = 'FAILED' WHERE event_type = 'TEST_EVENT' AND retry_count >= 3;

-- 4. 확인
SELECT event_type, status, retry_count FROM outbox WHERE event_type = 'TEST_EVENT';
```

## 성능 테스트

### 대량 Outbox 발행

```sql
-- 1000개 Outbox 생성
INSERT INTO outbox (id, event_type, aggregate_id, payload, status, created_at, retry_count)
SELECT 
    UUID(),
    'BULK_TEST',
    UUID(),
    '{"test": "data"}',
    'PENDING',
    NOW(),
    0
FROM 
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 ... UNION SELECT 1000) AS numbers;

-- OutboxScheduler 실행 시간 측정
-- 로그에서 확인
```

## 다음 단계

1. ✅ Outbox 기본 테스트 작성
2. ⏳ 테스트 실행 환경 수정 (Entity 생성 헬퍼)
3. ⏳ 통합 테스트 실행
4. ⏳ 외부 API 연동 (실제 HTTP 호출)
5. ⏳ 쿼리 로그 수집 구현
