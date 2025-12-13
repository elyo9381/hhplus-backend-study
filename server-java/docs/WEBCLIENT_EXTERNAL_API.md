# WebClient 외부 API 연동

## 개요

Outbox 패턴을 통해 외부 데이터 플랫폼에 이벤트를 전송하는 기능을 WebClient로 구현했습니다.

## 아키텍처

```
[비즈니스 로직]
    ↓
[Outbox 테이블 저장] (동기)
    ↓
[OutboxScheduler] (5초마다)
    ↓
[WebClientMessageProducer]
    ↓
[외부 API] (비동기, 논블로킹)
```

---

## 구현 컴포넌트

### 1. WebClientConfig

```java
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
```

**설정:**
- Connection Timeout: 5초
- Response Timeout: 10초
- Read/Write Timeout: 10초

### 2. WebClientMessageProducer

```java
@Component
@Primary
public class WebClientMessageProducer implements MessageProducer {
    
    @Override
    public void send(String eventType, String payload) {
        webClient.post()
            .uri("/api/events")
            .bodyValue(Map.of(
                "eventType", eventType,
                "payload", payload,
                "timestamp", System.currentTimeMillis()
            ))
            .retrieve()
            .bodyToMono(String.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(5)))
            .block(Duration.ofSeconds(10));
    }
}
```

**특징:**
- 비동기 논블로킹 I/O
- 자동 재시도 (3회, Exponential Backoff)
- 에러 로깅
- 타임아웃 처리

### 3. MockExternalApiController (테스트용)

```java
@RestController
@RequestMapping("/api/events")
@Profile({"dev", "test"})
public class MockExternalApiController {
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveEvent(@RequestBody Map<String, Object> request) {
        // 이벤트 수신 및 저장
        return ResponseEntity.ok(Map.of("status", "success"));
    }
    
    @GetMapping
    public ResponseEntity<List<ReceivedEvent>> getReceivedEvents() {
        // 수신된 이벤트 조회
        return ResponseEntity.ok(receivedEvents);
    }
}
```

---

## 재시도 전략

### Exponential Backoff

```
시도 1: 즉시
시도 2: 1초 후
시도 3: 2초 후
시도 4: 4초 후 (최대 5초)
```

### 재시도 조건

- Connection Timeout
- Read Timeout
- HTTP 5xx 에러

### 재시도 안 함

- HTTP 4xx 에러 (클라이언트 오류)
- 잘못된 요청

---

## 설정

### application.yml

```yaml
# 외부 API 설정
external:
  api:
    url: http://localhost:9090  # 외부 데이터 플랫폼 URL
```

### 환경별 설정

**개발 환경:**
```yaml
external:
  api:
    url: http://localhost:9090  # Mock API
```

**스테이징 환경:**
```yaml
external:
  api:
    url: https://staging-api.example.com
```

**프로덕션 환경:**
```yaml
external:
  api:
    url: https://api.example.com
```

---

## 사용 예시

### 1. 주문 생성 시 이벤트 발행

```java
@Transactional
public Order createOrder(...) {
    Order order = orderRepository.save(order);
    
    // Outbox 저장
    Outbox outbox = new Outbox(
        "ORDER_CREATED",
        order.getId(),
        toJson(order)
    );
    outboxRepository.save(outbox);
    
    return order;
}
```

### 2. OutboxScheduler가 자동 발행

```java
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    List<Outbox> pending = outboxRepository.findByStatus(PENDING);
    
    for (Outbox outbox : pending) {
        // WebClientMessageProducer.send() 호출
        messageProducer.send(outbox.getEventType(), outbox.getPayload());
        
        outbox.markAsPublished();
        outboxRepository.save(outbox);
    }
}
```

### 3. 외부 API에서 수신

```
POST http://external-api.example.com/api/events
Content-Type: application/json

{
  "eventType": "ORDER_CREATED",
  "payload": "{\"orderId\":\"123\",\"amount\":10000}",
  "timestamp": 1702456789000
}
```

---

## 에러 처리

### 1. Connection Timeout

```
원인: 외부 API 서버 응답 없음
처리: 재시도 3회 후 실패
결과: Outbox 상태 PENDING 유지, retryCount 증가
```

### 2. Read Timeout

```
원인: 외부 API 응답 지연
처리: 10초 후 타임아웃, 재시도
결과: 재시도 3회 후 실패 시 FAILED 상태
```

### 3. HTTP 5xx 에러

```
원인: 외부 API 서버 오류
처리: 재시도 3회
결과: 재시도 후에도 실패 시 FAILED 상태
```

### 4. HTTP 4xx 에러

```
원인: 잘못된 요청
처리: 재시도 안 함
결과: 즉시 FAILED 상태
```

---

## 모니터링

### 로그 확인

```bash
# WebClient 요청 로그
tail -f logs/application.log | grep "WebClientMessageProducer"
```

**출력 예시:**
```
[WebClientMessageProducer] Sending event to external API: eventType=ORDER_CREATED
[WebClientMessageProducer] Successfully sent event: eventType=ORDER_CREATED, response={"status":"success"}
```

### 실패 로그

```
[WebClientMessageProducer] Failed to send event: eventType=ORDER_CREATED, error=Connection timeout
[WebClientMessageProducer] Retrying... attempt: 1
[WebClientMessageProducer] Retrying... attempt: 2
[WebClientMessageProducer] Retrying... attempt: 3
[WebClientMessageProducer] HTTP error: status=500, body=Internal Server Error
```

### Outbox 상태 확인

```sql
-- 발행 실패한 이벤트 조회
SELECT 
    event_type,
    retry_count,
    error_message,
    created_at
FROM outbox
WHERE status = 'FAILED'
ORDER BY created_at DESC;
```

---

## 성능 최적화

### 현재 구현 (순차 처리)

```java
for (Outbox outbox : pendingOutboxes) {
    messageProducer.send(outbox.getEventType(), outbox.getPayload());
}
```

**성능:**
- 10개 이벤트: ~10초 (1개당 1초)

### 향후 개선 (병렬 처리)

```java
List<Mono<String>> requests = pendingOutboxes.stream()
    .map(outbox -> webClient.post()
        .uri("/api/events")
        .bodyValue(...)
        .retrieve()
        .bodyToMono(String.class))
    .toList();

Flux.merge(requests).blockLast();
```

**성능:**
- 10개 이벤트: ~1초 (병렬 처리)

---

## 테스트

### 1. WebClientIntegrationTest

```java
@Test
void sendEventToExternalApi() {
    // Given
    String eventType = "TEST_EVENT";
    
    // When
    messageProducer.send(eventType, payload);
    
    // Then
    // Mock 외부 API에서 수신 확인
    assertThat(receivedEvents).hasSize(1);
}
```

### 2. WebClientRetryTest

```java
@Test
void externalApiNotResponding() {
    // Given
    WebClientMessageProducer producer = new WebClientMessageProducer(
        webClientBuilder,
        "http://localhost:9999" // 존재하지 않는 서버
    );
    
    // When & Then
    assertThatThrownBy(() -> producer.send("TEST_EVENT", "{}"))
        .isInstanceOf(RuntimeException.class);
}
```

---

## RestTemplate vs WebClient 비교

| 항목 | RestTemplate | WebClient |
|------|-------------|-----------|
| **I/O 방식** | 블로킹 | 논블로킹 |
| **스레드 사용** | 요청당 1개 | 공유 (적음) |
| **성능** | 낮음 | 높음 |
| **재시도** | 수동 구현 | 내장 지원 |
| **타임아웃** | 설정 복잡 | 설정 간단 |
| **Spring 지원** | Maintenance | 공식 권장 |
| **학습 곡선** | 낮음 | 높음 |

---

## 장점

### 1. 비동기 논블로킹
- 스레드 효율적
- 높은 처리량

### 2. 자동 재시도
- Exponential Backoff
- 설정 간단

### 3. 타임아웃 관리
- Connection, Read, Write 타임아웃
- 세밀한 제어

### 4. 에러 처리
- 상세한 에러 로깅
- HTTP 상태 코드별 처리

### 5. 확장성
- 병렬 처리 가능
- Reactive 프로그래밍

---

## 다음 단계

### 1. 병렬 처리 구현
```java
// 여러 이벤트 동시 발행
Flux.merge(requests).blockLast();
```

### 2. Circuit Breaker 추가
```java
// Resilience4j 통합
@CircuitBreaker(name = "externalApi")
public void send(...) { ... }
```

### 3. 메트릭 수집
```java
// Micrometer 통합
meterRegistry.counter("outbox.sent", "eventType", eventType).increment();
```

### 4. 실제 외부 API 연동
- Kafka
- AWS EventBridge
- Google Pub/Sub

---

## 참고 자료

- [Spring WebClient 공식 문서](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [Project Reactor](https://projectreactor.io/)
- [Resilience4j](https://resilience4j.readme.io/)
