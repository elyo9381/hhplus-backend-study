# MSA 전환 설계 문서

## 1. 개요

### 1.1 현재 시스템 구조 (모놀리식)
```
┌─────────────────────────────────────────────────────────────┐
│                    E-Commerce Monolith                       │
├─────────────────────────────────────────────────────────────┤
│  User │ Product │ Order │ Payment │ Point │ Coupon │ Outbox │
├─────────────────────────────────────────────────────────────┤
│                      Single Database                         │
│                      Single Redis                            │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 MSA 전환 목표
- 도메인별 독립 배포 및 확장
- 장애 격리
- 팀별 자율적 개발/배포

---

## 2. 도메인 분리 설계

### 2.1 서비스 분리 단위

| 서비스 | 도메인 | 책임 | DB |
|--------|--------|------|-----|
| **user-service** | User | 사용자 관리, 인증 | user_db |
| **product-service** | Product | 상품 관리, 재고, 랭킹 | product_db |
| **order-service** | Order | 주문 생성/관리 | order_db |
| **payment-service** | Payment | 결제 처리 | payment_db |
| **point-service** | Point | 포인트 충전/사용 | point_db |
| **coupon-service** | Coupon | 쿠폰 발급/사용 | coupon_db |

### 2.2 MSA 아키텍처
```
                    ┌─────────────┐
                    │ API Gateway │
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           │               │               │
    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
    │   user-svc  │ │ product-svc │ │ coupon-svc  │
    │   (MySQL)   │ │(MySQL+Redis)│ │(MySQL+Redis)│
    └─────────────┘ └─────────────┘ └─────────────┘
           │               │               │
    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
    │  point-svc  │ │  order-svc  │ │ payment-svc │
    │   (MySQL)   │ │   (MySQL)   │ │   (MySQL)   │
    └─────────────┘ └─────────────┘ └─────────────┘
                           │
                    ┌──────▼──────┐
                    │ Message Bus │
                    │  (Kafka)    │
                    └─────────────┘
```

---

## 3. 도메인 간 의존성 분석

### 3.1 현재 의존성 맵
```
User ◄─────────────────────────────────────┐
  │                                         │
  ▼                                         │
Point ◄──────────────┐                      │
                     │                      │
Product ◄────────────┼──────────┐           │
  │                  │          │           │
  │ (재고차감)        │          │           │
  ▼                  │          │           │
Order ───────────────┼──────────┼───────────┤
  │                  │          │           │
  │ (주문조회)        │(포인트)   │(랭킹)     │(사용자)
  ▼                  │          │           │
Payment ─────────────┴──────────┴───────────┘

Coupon ◄─── User (사용자 쿠폰)
```

### 3.2 서비스 간 통신 방식

| 호출 | 방식 | 이유 |
|------|------|------|
| Order → Product (재고 차감) | **동기 (gRPC)** | 재고 확인 필수 |
| Payment → Order (주문 조회) | **동기 (gRPC)** | 결제 전 검증 필수 |
| Payment → Point (포인트 사용) | **동기 (gRPC)** | 잔액 확인 필수 |
| Payment → Product (랭킹) | **비동기 (Kafka)** | 비핵심 기능 |
| Order/Payment → Outbox | **비동기 (Kafka)** | 이벤트 발행 |

---

## 4. 트랜잭션 처리의 한계

### 4.1 모놀리식 vs MSA 트랜잭션

**모놀리식 (현재)**
```java
@Transactional
public Payment executePayment(UUID orderId, UUID userId) {
    Order order = orderRepository.findByIdWithLock(orderId);  // 같은 DB
    pointPort.usePoint(userId, amount);                        // 같은 DB
    Payment payment = paymentRepository.save(payment);         // 같은 DB
    order.completePayment(amount);
    orderRepository.save(order);                               // 같은 DB
    // → 모두 하나의 트랜잭션으로 원자성 보장
}
```

**MSA (분리 후)**
```
Payment Service                Order Service              Point Service
      │                              │                          │
      │──── 1. 주문 조회 ────────────►│                          │
      │◄─── 주문 정보 ────────────────│                          │
      │                              │                          │
      │──── 2. 포인트 사용 ──────────────────────────────────────►│
      │◄─── 성공 ────────────────────────────────────────────────│
      │                              │                          │
      │──── 3. 주문 상태 변경 ────────►│                          │
      │     (여기서 실패하면?)         │                          │
      │                              │                          │
```

### 4.2 분산 트랜잭션의 문제점

| 문제 | 설명 | 예시 |
|------|------|------|
| **부분 실패** | 일부 서비스만 성공 | 포인트 차감 후 주문 상태 변경 실패 |
| **네트워크 장애** | 서비스 간 통신 실패 | 타임아웃, 연결 끊김 |
| **데이터 불일치** | 서비스 간 데이터 동기화 지연 | 재고는 차감됐는데 주문은 실패 |
| **롤백 불가** | 다른 서비스의 변경 취소 어려움 | 이미 커밋된 포인트 차감 |

### 4.3 2PC(Two-Phase Commit)의 한계
- 모든 서비스가 동시에 가용해야 함
- 락 점유 시간 증가 → 성능 저하
- 단일 장애점(Coordinator) 발생
- MSA에서는 **권장하지 않음**

---

## 5. 해결방안: Saga 패턴

### 5.1 Saga 패턴 개요
- 각 서비스의 로컬 트랜잭션을 순차적으로 실행
- 실패 시 **보상 트랜잭션(Compensating Transaction)** 실행
- 최종 일관성(Eventual Consistency) 보장

### 5.2 Choreography vs Orchestration

**Choreography (이벤트 기반)**
```
┌─────────┐    OrderCreated    ┌─────────┐    StockReserved    ┌─────────┐
│  Order  │ ─────────────────► │ Product │ ─────────────────► │ Payment │
│ Service │                    │ Service │                    │ Service │
└─────────┘                    └─────────┘                    └─────────┘
     ▲                              │                              │
     │         StockFailed          │       PaymentCompleted       │
     └──────────────────────────────┴──────────────────────────────┘
```

**Orchestration (중앙 조정자)**
```
                    ┌──────────────────┐
                    │  Order Saga      │
                    │  Orchestrator    │
                    └────────┬─────────┘
           ┌─────────────────┼─────────────────┐
           │                 │                 │
           ▼                 ▼                 ▼
    ┌──────────┐      ┌──────────┐      ┌──────────┐
    │ Product  │      │  Point   │      │ Payment  │
    │ Service  │      │ Service  │      │ Service  │
    └──────────┘      └──────────┘      └──────────┘
```

### 5.3 결제 플로우 Saga 설계 (Orchestration)

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Payment Saga Orchestrator                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  [정상 플로우]                                                        │
│  1. Order Service    → 주문 조회 & 상태 검증                          │
│  2. Point Service    → 포인트 차감                                    │
│  3. Payment Service  → 결제 생성 & 완료                               │
│  4. Order Service    → 주문 상태 PAID로 변경                          │
│  5. Event 발행       → PaymentCompleted (랭킹, 데이터플랫폼)           │
│                                                                      │
│  [보상 트랜잭션 - Step 3 실패 시]                                      │
│  C2. Point Service   → 포인트 환불                                    │
│                                                                      │
│  [보상 트랜잭션 - Step 4 실패 시]                                      │
│  C3. Payment Service → 결제 취소                                      │
│  C2. Point Service   → 포인트 환불                                    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. 상세 설계

### 6.1 주문 생성 Saga

```java
// Order Saga Orchestrator
public class OrderSagaOrchestrator {
    
    public OrderResult createOrder(CreateOrderCommand cmd) {
        SagaState state = new SagaState();
        
        try {
            // Step 1: 재고 예약 (Product Service)
            ReserveStockResult stockResult = productClient.reserveStock(cmd.getItems());
            state.setStockReserved(true);
            state.setReservationId(stockResult.getReservationId());
            
            // Step 2: 주문 생성 (Order Service - 로컬)
            Order order = orderService.createOrder(cmd, stockResult.getSnapshots());
            state.setOrderId(order.getId());
            
            // Step 3: 재고 확정 (Product Service)
            productClient.confirmStock(state.getReservationId());
            
            return OrderResult.success(order);
            
        } catch (Exception e) {
            // 보상 트랜잭션
            compensate(state);
            throw e;
        }
    }
    
    private void compensate(SagaState state) {
        if (state.isStockReserved()) {
            productClient.cancelReservation(state.getReservationId());
        }
    }
}
```

### 6.2 결제 Saga

```java
// Payment Saga Orchestrator
public class PaymentSagaOrchestrator {
    
    public PaymentResult executePayment(PaymentCommand cmd) {
        SagaState state = new SagaState();
        
        try {
            // Step 1: 주문 검증 (Order Service)
            OrderInfo order = orderClient.getOrderForPayment(cmd.getOrderId());
            validateOrder(order, cmd.getUserId());
            
            // Step 2: 포인트 차감 (Point Service)
            pointClient.usePoint(cmd.getUserId(), order.getFinalAmount());
            state.setPointDeducted(true);
            state.setDeductedAmount(order.getFinalAmount());
            
            // Step 3: 결제 생성 (Payment Service - 로컬)
            Payment payment = paymentService.createPayment(cmd, order);
            state.setPaymentId(payment.getId());
            
            // Step 4: 주문 상태 변경 (Order Service)
            orderClient.completeOrder(cmd.getOrderId());
            
            // Step 5: 이벤트 발행 (비동기)
            eventPublisher.publish(new PaymentCompletedEvent(payment));
            
            return PaymentResult.success(payment);
            
        } catch (Exception e) {
            compensate(state, cmd);
            throw e;
        }
    }
    
    private void compensate(SagaState state, PaymentCommand cmd) {
        // 역순으로 보상
        if (state.getPaymentId() != null) {
            paymentService.cancelPayment(state.getPaymentId());
        }
        if (state.isPointDeducted()) {
            pointClient.refundPoint(cmd.getUserId(), state.getDeductedAmount());
        }
    }
}
```

### 6.3 Saga 상태 관리

```java
@Entity
@Table(name = "saga_instances")
public class SagaInstance {
    @Id
    private UUID id;
    
    private String sagaType;        // ORDER_SAGA, PAYMENT_SAGA
    private String currentStep;     // RESERVE_STOCK, DEDUCT_POINT, ...
    private SagaStatus status;      // STARTED, COMPLETED, COMPENSATING, FAILED
    
    @Column(columnDefinition = "JSON")
    private String payload;         // 명령 데이터
    
    @Column(columnDefinition = "JSON")
    private String state;           // 각 단계 결과
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

---

## 7. 이벤트 기반 통신

### 7.1 도메인 이벤트 정의

```java
// 주문 이벤트
public record OrderCreatedEvent(UUID orderId, UUID userId, List<OrderItem> items) {}
public record OrderCompletedEvent(UUID orderId, OrderStatus status) {}
public record OrderCancelledEvent(UUID orderId, String reason) {}

// 결제 이벤트
public record PaymentCompletedEvent(UUID paymentId, UUID orderId, Long amount) {}
public record PaymentFailedEvent(UUID orderId, String reason) {}

// 재고 이벤트
public record StockReservedEvent(UUID reservationId, List<StockItem> items) {}
public record StockConfirmedEvent(UUID reservationId) {}
public record StockReleasedEvent(UUID reservationId, String reason) {}

// 포인트 이벤트
public record PointDeductedEvent(UUID userId, Long amount, UUID referenceId) {}
public record PointRefundedEvent(UUID userId, Long amount, UUID referenceId) {}
```

### 7.2 Kafka 토픽 설계

| 토픽 | Producer | Consumer | 용도 |
|------|----------|----------|------|
| `order.created` | order-svc | product-svc | 재고 예약 트리거 |
| `stock.reserved` | product-svc | order-svc | 재고 예약 완료 |
| `stock.failed` | product-svc | order-svc | 재고 부족 알림 |
| `payment.completed` | payment-svc | order-svc, product-svc | 결제 완료 |
| `payment.failed` | payment-svc | point-svc | 포인트 환불 트리거 |

### 7.3 Outbox 패턴 (서비스별)

```java
// 각 서비스에서 Outbox 테이블 사용
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;
    private String aggregateType;   // ORDER, PAYMENT, STOCK
    private UUID aggregateId;
    private String eventType;
    private String payload;
    private OutboxStatus status;
    private LocalDateTime createdAt;
}

// 트랜잭션 내에서 Outbox 저장
@Transactional
public Payment createPayment(PaymentCommand cmd) {
    Payment payment = new Payment(...);
    paymentRepository.save(payment);
    
    // 같은 트랜잭션에서 Outbox 저장
    outboxRepository.save(new OutboxEvent(
        "PAYMENT", payment.getId(), "PAYMENT_COMPLETED", toJson(payment)
    ));
    
    return payment;
}

// Debezium CDC 또는 Polling으로 Kafka 발행
```

---

## 8. 데이터 정합성 보장

### 8.1 멱등성 처리

```java
// Payment Service - 멱등성 키 사용
@Transactional
public Payment processPayment(String idempotencyKey, PaymentCommand cmd) {
    // 이미 처리된 요청인지 확인
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        return existing.get();  // 기존 결과 반환
    }
    
    // 새로운 결제 처리
    Payment payment = createPayment(cmd);
    payment.setIdempotencyKey(idempotencyKey);
    return paymentRepository.save(payment);
}
```

### 8.2 이벤트 중복 처리

```java
// Consumer 측 중복 처리
@KafkaListener(topics = "payment.completed")
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    // 이미 처리된 이벤트인지 확인
    if (processedEventRepository.exists(event.getEventId())) {
        log.info("이미 처리된 이벤트: {}", event.getEventId());
        return;
    }
    
    // 이벤트 처리
    processEvent(event);
    
    // 처리 완료 기록
    processedEventRepository.save(event.getEventId());
}
```

### 8.3 최종 일관성 모니터링

```java
// 정합성 체크 배치
@Scheduled(cron = "0 0 * * * *")  // 매시간
public void checkConsistency() {
    // Order-Payment 정합성
    List<Order> paidOrders = orderRepository.findByStatus(PAID);
    for (Order order : paidOrders) {
        Optional<Payment> payment = paymentClient.findByOrderId(order.getId());
        if (payment.isEmpty() || payment.get().getStatus() != COMPLETED) {
            alertService.sendInconsistencyAlert(order.getId());
        }
    }
}
```

---

## 9. 서비스별 API 설계

### 9.1 Product Service API

```yaml
# gRPC 정의
service ProductService {
  rpc ReserveStock(ReserveStockRequest) returns (ReserveStockResponse);
  rpc ConfirmStock(ConfirmStockRequest) returns (Empty);
  rpc CancelReservation(CancelReservationRequest) returns (Empty);
  rpc GetProduct(GetProductRequest) returns (Product);
}

# REST API (외부용)
GET  /api/products/{id}
GET  /api/products
POST /api/products
GET  /api/products/ranking/daily
GET  /api/products/ranking/weekly
```

### 9.2 Point Service API

```yaml
# gRPC 정의
service PointService {
  rpc UsePoint(UsePointRequest) returns (UsePointResponse);
  rpc RefundPoint(RefundPointRequest) returns (Empty);
  rpc GetBalance(GetBalanceRequest) returns (BalanceResponse);
}

# REST API (외부용)
GET  /api/users/{userId}/points
POST /api/users/{userId}/points/charge
```

### 9.3 Order Service API

```yaml
# gRPC 정의
service OrderService {
  rpc GetOrderForPayment(GetOrderRequest) returns (OrderInfo);
  rpc CompleteOrder(CompleteOrderRequest) returns (Empty);
  rpc CancelOrder(CancelOrderRequest) returns (Empty);
}

# REST API (외부용)
POST /api/orders
GET  /api/orders/{id}
```

---

## 10. 장애 대응

### 10.1 서킷 브레이커

```java
@Service
public class ProductClientWithCircuitBreaker {
    
    private final CircuitBreaker circuitBreaker;
    private final ProductClient productClient;
    
    @CircuitBreaker(name = "product-service", fallbackMethod = "reserveStockFallback")
    public ReserveStockResult reserveStock(List<StockItem> items) {
        return productClient.reserveStock(items);
    }
    
    public ReserveStockResult reserveStockFallback(List<StockItem> items, Exception e) {
        log.error("Product Service 장애, 재고 예약 실패", e);
        throw new ServiceUnavailableException("상품 서비스 일시 장애");
    }
}
```

### 10.2 재시도 정책

```yaml
# application.yml
resilience4j:
  retry:
    instances:
      product-service:
        maxAttempts: 3
        waitDuration: 1s
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
  circuitbreaker:
    instances:
      product-service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

### 10.3 Dead Letter Queue

```java
// 처리 실패 이벤트 DLQ로 이동
@KafkaListener(topics = "payment.completed")
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    try {
        processEvent(event);
    } catch (Exception e) {
        // DLQ로 전송
        kafkaTemplate.send("payment.completed.dlq", event);
        log.error("이벤트 처리 실패, DLQ로 이동: {}", event.getEventId(), e);
    }
}

// DLQ 모니터링 및 재처리
@Scheduled(fixedDelay = 60000)
public void processDlq() {
    // DLQ 메시지 조회 및 재처리 시도
}
```

---

## 11. 마이그레이션 전략

### 11.1 단계별 전환

```
Phase 1: Strangler Fig 패턴
┌─────────────────────────────────────────┐
│              API Gateway                 │
│  (라우팅: 신규 → MSA, 기존 → Monolith)   │
└─────────────────────────────────────────┘
         │                    │
         ▼                    ▼
┌─────────────┐      ┌─────────────────┐
│ coupon-svc  │      │    Monolith     │
│   (신규)    │      │ (User,Product,  │
└─────────────┘      │  Order,Payment, │
                     │  Point)         │
                     └─────────────────┘

Phase 2: 점진적 분리
- Product Service 분리
- Point Service 분리
- Order/Payment Service 분리

Phase 3: 완전 분리
- 모놀리식 제거
- 모든 서비스 독립 운영
```

### 11.2 데이터 마이그레이션

```sql
-- 1. 신규 서비스 DB에 테이블 생성
-- 2. CDC(Change Data Capture)로 실시간 동기화
-- 3. 트래픽 전환
-- 4. 기존 테이블 제거
```

---

## 12. 결론

### 12.1 MSA 전환 시 핵심 고려사항

| 항목 | 모놀리식 | MSA |
|------|----------|-----|
| 트랜잭션 | ACID (단일 DB) | Saga (최종 일관성) |
| 통신 | 메서드 호출 | gRPC/Kafka |
| 데이터 | 단일 DB | 서비스별 DB |
| 배포 | 전체 배포 | 독립 배포 |
| 장애 | 전체 영향 | 격리 가능 |

### 12.2 트레이드오프

**장점**
- 독립적 확장/배포
- 장애 격리
- 기술 스택 다양화
- 팀 자율성

**단점**
- 분산 트랜잭션 복잡성
- 네트워크 지연
- 운영 복잡도 증가
- 데이터 정합성 관리

### 12.3 권장 사항

1. **Saga 패턴**: Orchestration 방식 권장 (복잡한 비즈니스 로직)
2. **이벤트 기반**: 비핵심 기능은 비동기 처리
3. **Outbox 패턴**: 이벤트 발행 보장
4. **멱등성**: 모든 API/이벤트 핸들러에 적용
5. **모니터링**: 분산 트레이싱, 정합성 체크 필수
