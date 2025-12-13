# 쿼리 로그 수집 & Outbox 패턴 통합 아키텍처

## 목차
1. [개요](#개요)
2. [아키텍처 비교](#아키텍처-비교)
3. [쿼리 로그 수집](#쿼리-로그-수집)
4. [Outbox 패턴](#outbox-패턴)
5. [Application Event](#application-event)
6. [통합 아키텍처](#통합-아키텍처)
7. [구현 가이드](#구현-가이드)
8. [실험 시나리오](#실험-시나리오)

---

## 개요

본 프로젝트는 세 가지 비동기 처리 메커니즘을 통합하여 운영합니다:

| 메커니즘 | 목적 | 처리 방식 | 유실 허용 |
|---------|------|----------|----------|
| **쿼리 로그 수집** | 성능 모니터링 | 비동기 큐 → 배치 인서트 | ✅ 허용 |
| **Outbox 패턴** | 외부 메시지 발행 | 동기 저장 → 스케줄러 발행 | ❌ 불가 |
| **Application Event** | 내부 이벤트 처리 | @EventListener + @Async | ✅ 허용 |

---

## 아키텍처 비교

### 1. 쿼리 로그 수집 (비동기 큐 방식)

```
[쿼리 실행]
    ↓
[Hibernate Interceptor 감지]
    ↓
[ApplicationEvent 발행] (비동기)
    ↓
[@EventListener 수신] (@Async)
    ↓
[BlockingQueue 적재]
    ↓
[배치 프로세서] (@Scheduled)
    ↓
[벌크 인서트] → query_log 테이블
```

**특징:**
- ✅ 성능 영향 최소 (비동기)
- ✅ DB 부하 최소 (배치 처리)
- ⚠️ 애플리케이션 종료 시 큐 데이터 유실 가능
- ⚠️ 트랜잭션 분리 (원본 비즈니스와 별개)

### 2. Outbox 패턴 (동기 저장 방식)

```
[OrderService.createOrder()]
    @Transactional 시작
    ↓
    ├─ Order 저장
    ├─ Outbox 저장 (동기, 같은 트랜잭션) ← 핵심!
    ↓
    커밋 (Order + Outbox 함께)


[OutboxScheduler] (별도 스레드)
    @Scheduled(fixedDelay = 5000)
    ↓
    ├─ Outbox 테이블에서 PENDING 조회
    ├─ 외부 시스템에 발행 (Kafka, HTTP)
    └─ PUBLISHED로 업데이트
```

**특징:**
- ✅ 트랜잭션 정합성 100% 보장
- ✅ 절대 유실 안 됨 (DB 저장)
- ❌ 동기 저장 (성능 영향)
- ❌ 배치 처리 불가 (트랜잭션 내 즉시 저장)

### 3. Application Event (내부 이벤트)

```
[비즈니스 로직]
    ↓
[eventPublisher.publishEvent()]
    ↓
[@EventListener] (@Async)
    ↓
[내부 처리] (이메일, 알림, 통계 등)
```

**특징:**
- ✅ 간단한 구현
- ✅ 같은 JVM 내 통신
- ⚠️ 외부 시스템 연동 부적합
- ⚠️ 트랜잭션 정합성 보장 안 됨

---

## 쿼리 로그 수집

### 목적
- 쿼리 성능 모니터링
- 슬로우 쿼리 식별
- 인덱스 최적화 근거 수집
- DB 부하 분석

### 수집 데이터

```java
public class QueryLog {
    private UUID id;
    private String queryType;        // SELECT, INSERT, UPDATE, DELETE
    private String sql;              // 실제 실행된 SQL
    private Long executionTimeMs;    // 실행 시간 (밀리초)
    private LocalDateTime executedAt;
    private String callerMethod;     // 호출한 메서드
    private String threadName;       // 실행 스레드
    private Boolean success;         // 성공/실패
    private String errorMessage;     // 에러 메시지
}
```

### 처리 흐름

```java
// 1. Hibernate Interceptor
public class QueryCollectorInterceptor extends EmptyInterceptor {
    @Override
    public String onPrepareStatement(String sql) {
        long startTime = System.currentTimeMillis();
        
        // 쿼리 실행 후
        long executionTime = System.currentTimeMillis() - startTime;
        
        eventPublisher.publishEvent(new QueryExecutedEvent(
            sql, executionTime, extractQueryType(sql)
        ));
        
        return sql;
    }
}

// 2. Event Listener (비동기)
@Component
public class QueryLogCollector {
    private final BlockingQueue<QueryLog> queue = new LinkedBlockingQueue<>(10000);
    
    @EventListener
    @Async("queryCollectorExecutor")
    public void collectQueryLog(QueryExecutedEvent event) {
        QueryLog log = new QueryLog(
            event.getSql(),
            event.getExecutionTime(),
            event.getQueryType()
        );
        queue.offer(log);
    }
    
    // 3. 배치 프로세서
    @Scheduled(fixedDelay = 10000) // 10초마다
    public void flushLogs() {
        List<QueryLog> logs = new ArrayList<>();
        queue.drainTo(logs, 100); // 최대 100개
        
        if (!logs.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_SQL, logs);
        }
    }
}
```

### 설정

```java
@Configuration
@EnableAsync
public class QueryCollectorConfig {
    
    @Bean(name = "queryCollectorExecutor")
    public Executor queryCollectorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("query-collector-");
        executor.initialize();
        return executor;
    }
}
```

### 테이블 설계

```sql
CREATE TABLE query_log (
    id BINARY(16) PRIMARY KEY,
    query_type VARCHAR(20) NOT NULL,
    sql TEXT NOT NULL,
    execution_time_ms BIGINT NOT NULL,
    executed_at DATETIME(6) NOT NULL,
    caller_method VARCHAR(255),
    thread_name VARCHAR(100),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    
    INDEX idx_query_type_executed_at (query_type, executed_at),
    INDEX idx_execution_time (execution_time_ms DESC),
    INDEX idx_executed_at (executed_at)
) PARTITION BY RANGE (TO_DAYS(executed_at)) (
    PARTITION p_2025_12_13 VALUES LESS THAN (TO_DAYS('2025-12-14')),
    PARTITION p_2025_12_14 VALUES LESS THAN (TO_DAYS('2025-12-15')),
    -- 자동 파티션 추가 스크립트 필요
);
```

---

## Outbox 패턴

### 목적
- 외부 시스템 메시지 발행 (Kafka, HTTP API 등)
- 트랜잭션 정합성 보장
- At-Least-Once 전송 보장
- 재시도 메커니즘

### Outbox 데이터

```java
public class Outbox {
    private UUID id;
    private String eventType;        // ORDER_CREATED, PAYMENT_COMPLETED
    private UUID aggregateId;        // Order ID, Payment ID
    private String payload;          // JSON 직렬화된 이벤트 데이터
    private OutboxStatus status;     // PENDING, PUBLISHED, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private Integer retryCount;
    private String errorMessage;
}

public enum OutboxStatus {
    PENDING,    // 발행 대기
    PUBLISHED,  // 발행 완료
    FAILED      // 발행 실패 (재시도 초과)
}
```

### 처리 흐름

```java
// 1. 비즈니스 로직에서 동기 저장
@Service
public class OrderService {
    
    @Transactional
    public Order createOrder(UUID userId, List<OrderItemRequest> items) {
        // 주문 생성
        Order order = new Order(userId, items);
        orderRepository.save(order);
        
        // Outbox 저장 (같은 트랜잭션)
        Outbox outbox = new Outbox(
            "ORDER_CREATED",
            order.getId(),
            toJson(order)
        );
        outboxRepository.save(outbox);
        
        return order;
    }
}

// 2. 스케줄러가 발행
@Component
public class OutboxScheduler {
    
    private final OutboxRepository outboxRepository;
    private final MessageProducer messageProducer;
    
    @Scheduled(fixedDelay = 5000) // 5초마다
    @Transactional
    public void publishPendingEvents() {
        List<Outbox> pending = outboxRepository
            .findByStatusAndRetryCountLessThan(PENDING, 3);
        
        for (Outbox outbox : pending) {
            try {
                // 외부 시스템에 발행
                messageProducer.send(
                    outbox.getEventType(), 
                    outbox.getPayload()
                );
                
                outbox.markAsPublished();
                outboxRepository.save(outbox);
                
            } catch (Exception e) {
                outbox.incrementRetry();
                outbox.setErrorMessage(e.getMessage());
                outboxRepository.save(outbox);
                
                if (outbox.getRetryCount() >= 3) {
                    outbox.markAsFailed();
                    outboxRepository.save(outbox);
                }
            }
        }
    }
}
```

### MessageProducer Port

```java
// Port 인터페이스
public interface MessageProducer {
    void send(String eventType, String payload);
}

// Mock 구현 (테스트용)
@Component
@Profile("test")
public class MockMessageProducer implements MessageProducer {
    private final List<Message> sentMessages = new ArrayList<>();
    
    @Override
    public void send(String eventType, String payload) {
        sentMessages.add(new Message(eventType, payload));
        System.out.println("Mock sent: " + eventType);
    }
    
    public List<Message> getSentMessages() {
        return sentMessages;
    }
}

// Kafka 구현 (실제 운영)
@Component
@Profile("prod")
public class KafkaMessageProducer implements MessageProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Override
    public void send(String eventType, String payload) {
        kafkaTemplate.send("order-events", eventType, payload);
    }
}
```

### 테이블 설계

```sql
CREATE TABLE outbox (
    id BINARY(16) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    
    INDEX idx_status_retry (status, retry_count),
    INDEX idx_created_at (created_at)
);
```

---

## Application Event

### 목적
- 도메인 이벤트 발행 (내부 시스템)
- 비동기 후처리 (이메일, 알림, 통계)
- 느슨한 결합 (Decoupling)

### 이벤트 정의

```java
// 도메인 이벤트
public class OrderCreatedEvent {
    private final Order order;
    private final LocalDateTime occurredAt;
    
    public OrderCreatedEvent(Order order) {
        this.order = order;
        this.occurredAt = LocalDateTime.now();
    }
}

public class PaymentCompletedEvent {
    private final Payment payment;
    private final LocalDateTime occurredAt;
    
    public PaymentCompletedEvent(Payment payment) {
        this.payment = payment;
        this.occurredAt = LocalDateTime.now();
    }
}
```

### 이벤트 발행

```java
@Service
public class OrderService {
    
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public Order createOrder(UUID userId, List<OrderItemRequest> items) {
        Order order = new Order(userId, items);
        orderRepository.save(order);
        
        Outbox outbox = new Outbox(...);
        outboxRepository.save(outbox);
        
        // Application Event 발행 (비동기)
        eventPublisher.publishEvent(new OrderCreatedEvent(order));
        
        return order;
    }
}
```

### 이벤트 리스너

```java
@Component
public class OrderEventListener {
    
    // 이메일 발송
    @EventListener
    @Async
    public void sendOrderConfirmationEmail(OrderCreatedEvent event) {
        emailService.sendOrderConfirmation(event.getOrder());
    }
    
    // 통계 업데이트
    @EventListener
    @Async
    public void updateOrderStatistics(OrderCreatedEvent event) {
        statisticsService.incrementOrderCount();
    }
    
    // 캐시 무효화
    @EventListener
    @Async
    public void invalidateCache(OrderCreatedEvent event) {
        cacheManager.evict("orders", event.getOrder().getUserId());
    }
}
```

---

## 통합 아키텍처

### 전체 흐름도

```
[OrderService.createOrder()]
    @Transactional 시작
    ↓
    ├─ 1. Order 저장 (동기)
    ├─ 2. Outbox 저장 (동기, 같은 트랜잭션) ← 외부 메시지용
    ├─ 3. ApplicationEvent 발행 (비동기) ← 내부 처리용
    ↓
    트랜잭션 커밋
    
    
    ↓ (비동기 분기)
    
    
[EventListener] (@Async)
    ↓
    ├─ 이메일 발송
    ├─ 통계 업데이트
    ├─ 캐시 무효화
    └─ 쿼리 로그 수집 큐에 적재
    
    
[OutboxScheduler] (별도 스레드)
    @Scheduled(fixedDelay = 5000)
    ↓
    ├─ Outbox 테이블에서 PENDING 조회
    ├─ 외부 시스템에 발행 (Kafka, HTTP)
    └─ PUBLISHED로 업데이트
    
    
[QueryLogBatchProcessor] (별도 스레드)
    @Scheduled(fixedDelay = 10000)
    ↓
    ├─ 큐에서 로그 꺼내기 (최대 100개)
    └─ 배치 벌크 인서트
```

### 통합 코드 예시

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductPort productPort;
    
    @Transactional
    public Order createOrder(UUID userId, List<OrderItemRequest> items) {
        // 1. 비즈니스 로직
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequest item : items) {
            ProductSnapshot snapshot = productPort.decreaseStockWithSnapshot(
                item.productId(), 
                item.quantity()
            );
            orderItems.add(new OrderItem(
                snapshot.productId(),
                snapshot.productName(),
                snapshot.unitPrice(),
                item.quantity()
            ));
        }
        
        Order order = new Order(userId, orderItems);
        orderRepository.save(order);
        
        // 2. Outbox 저장 (동기, 같은 트랜잭션)
        Outbox outbox = new Outbox(
            "ORDER_CREATED",
            order.getId(),
            toJson(Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "totalAmount", order.getTotalAmount(),
                "items", order.getItems()
            ))
        );
        outboxRepository.save(outbox);
        
        // 3. Application Event 발행 (비동기)
        eventPublisher.publishEvent(new OrderCreatedEvent(order));
        
        return order;
    }
    
    private String toJson(Object obj) {
        // JSON 직렬화
        return objectMapper.writeValueAsString(obj);
    }
}
```

---

## 구현 가이드

### 패키지 구조

```
infrastructure/external/
├── query/
│   ├── QueryLog.java
│   ├── QueryCollector.java
│   ├── QueryBatchProcessor.java
│   ├── QueryCollectorInterceptor.java
│   └── persistence/
│       ├── QueryLogEntity.java
│       └── QueryLogRepository.java
├── outbox/
│   ├── OutboxScheduler.java
│   └── persistence/
│       ├── OutboxEntity.java
│       └── OutboxRepository.java
├── message/
│   ├── MockMessageProducer.java
│   └── KafkaMessageProducer.java
└── config/
    ├── QueryCollectorConfig.java
    ├── OutboxConfig.java
    └── AsyncConfig.java

domain/outbox/
├── Outbox.java
├── OutboxRepository.java (인터페이스)
└── OutboxStatus.java

application/outbox/
├── OutboxPublisher.java
└── MessageProducer.java (Port 인터페이스)
```

### 구현 순서

**Phase 1: 쿼리 로그 수집 (2일)**
1. QueryLog 도메인 모델
2. QueryCollectorInterceptor 구현
3. QueryCollector (큐 적재)
4. QueryBatchProcessor (배치 인서트)
5. 테이블 생성 및 파티셔닝

**Phase 2: Outbox 패턴 (2일)**
6. Outbox 도메인 모델
7. OutboxRepository
8. MessageProducer Port 인터페이스
9. MockMessageProducer 구현
10. OutboxScheduler 구현

**Phase 3: 통합 (1일)**
11. OrderService에 Outbox 저장 추가
12. Application Event 발행
13. EventListener 구현
14. 통합 테스트

**Phase 4: 고도화 (2일)**
15. 재시도 로직 개선
16. 모니터링 대시보드
17. 파티션 자동 관리
18. 성능 튜닝

---

## 실험 시나리오

### 1. 트랜잭션 정합성 검증

**시나리오: Order 저장 실패 시 Outbox도 롤백되는가?**

```java
@Test
void testTransactionalConsistency() {
    // Given
    UUID userId = UUID.randomUUID();
    List<OrderItemRequest> items = List.of(
        new OrderItemRequest(invalidProductId, 1) // 존재하지 않는 상품
    );
    
    // When & Then
    assertThrows(ProductNotFoundException.class, () -> {
        orderService.createOrder(userId, items);
    });
    
    // Outbox도 저장되지 않아야 함
    List<Outbox> outboxes = outboxRepository.findAll();
    assertThat(outboxes).isEmpty();
}
```

### 2. 쿼리 성능 분석

**시나리오: 인덱스 추가 전/후 성능 비교**

```sql
-- 인덱스 추가 전
SELECT AVG(execution_time_ms) as avg_time
FROM query_log
WHERE query_type = 'SELECT'
  AND sql LIKE '%orders%'
  AND executed_at > NOW() - INTERVAL 1 HOUR;

-- 인덱스 추가
CREATE INDEX idx_user_id ON orders(user_id);

-- 인덱스 추가 후 (동일 쿼리 재실행)
-- 평균 실행 시간 비교
```

### 3. Outbox 발행 지연 측정

**시나리오: Outbox 저장부터 발행까지 걸리는 시간**

```sql
SELECT 
    event_type,
    AVG(TIMESTAMPDIFF(SECOND, created_at, published_at)) as avg_delay_sec,
    MAX(TIMESTAMPDIFF(SECOND, created_at, published_at)) as max_delay_sec
FROM outbox
WHERE status = 'PUBLISHED'
  AND created_at > NOW() - INTERVAL 1 DAY
GROUP BY event_type;
```

### 4. 배치 크기 최적화

**시나리오: 배치 크기별 성능 비교**

```java
// 10개씩 배치
@Scheduled(fixedDelay = 10000)
public void flushLogs() {
    queue.drainTo(logs, 10);
    jdbcTemplate.batchUpdate(INSERT_SQL, logs);
}

// 100개씩 배치
@Scheduled(fixedDelay = 10000)
public void flushLogs() {
    queue.drainTo(logs, 100);
    jdbcTemplate.batchUpdate(INSERT_SQL, logs);
}

// 쿼리 로그에서 INSERT 시간 비교
SELECT 
    COUNT(*) as batch_size,
    AVG(execution_time_ms) as avg_time
FROM query_log
WHERE sql LIKE '%INSERT INTO query_log%'
GROUP BY DATE(executed_at);
```

### 5. 파티셔닝 효과 측정

**시나리오: 파티션 프루닝 효과**

```sql
-- 파티셔닝 전
EXPLAIN SELECT * FROM query_log 
WHERE executed_at BETWEEN '2025-12-13' AND '2025-12-14';

-- 파티셔닝 후
ALTER TABLE query_log 
PARTITION BY RANGE (TO_DAYS(executed_at));

-- 동일 쿼리 실행 계획 비교
-- rows examined 감소 확인
```

---

## 주의사항

### Outbox 패턴

**❌ 하지 말아야 할 것:**
- Outbox를 비동기 큐로 처리 (트랜잭션 정합성 깨짐)
- 배치로 Outbox 저장 (유실 가능)
- Application Event로 Outbox 저장 (트랜잭션 분리)

**✅ 해야 할 것:**
- 비즈니스 트랜잭션 내에서 동기 저장
- 재시도 로직 구현
- 실패 이벤트 모니터링
- 멱등성 보장 (중복 발행 대응)

### 쿼리 로그 수집

**❌ 하지 말아야 할 것:**
- 프로덕션에서 100% 수집 (성능 영향)
- SQL 길이 제한 없이 저장 (메모리 오버플로우)
- 큐 사이즈 무제한 (메모리 부족)

**✅ 해야 할 것:**
- 샘플링 적용 (10% 수집)
- SQL 길이 제한 (1000자)
- 큐 사이즈 제한 (10000개)
- 애플리케이션 종료 시 큐 flush

### Application Event

**❌ 하지 말아야 할 것:**
- 크리티컬한 비즈니스 로직에 사용
- 외부 시스템 연동에 사용
- 트랜잭션 정합성 기대

**✅ 해야 할 것:**
- 내부 이벤트 처리에만 사용
- 실패해도 괜찮은 작업에만 사용
- 비동기 스레드풀 크기 제한

---

## 참고 자료

- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Spring Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Hibernate Interceptor](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#events-interceptors)
- [MySQL Partitioning](https://dev.mysql.com/doc/refman/8.0/en/partitioning.html)
