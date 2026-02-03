# 대규모 대기열 아키텍처 (1억 명 대응)

## 개요

선착순 쿠폰 발급, 티켓팅, 예약 시스템 등에서 대규모 트래픽을 처리하기 위한 대기열 아키텍처 설계 문서입니다.

## 문제 정의

### 기존 방식의 한계

```java
// 기존: DB COUNT 기반 순위 (Race Condition 발생)
long currentCount = userCouponRepository.countByCouponId(couponId);
int rank = (int) currentCount + 1;  // 동시 요청 시 중복 순위 발생
```

**문제점**:
- Kafka Consumer 여러 개가 동시에 처리 시 같은 count를 읽음
- 순위 중복 발생

### 개선: Redis INCR

```java
// 개선: Redis INCR (원자적 연산)
long rank = redisRepository.incrementRank(couponId);
```

**한계**:
- 단일 Redis: ~10만 TPS
- 1억 명이 10분에 몰리면: ~16만 TPS 필요
- 병목 발생

---

## 아키텍처 옵션 비교

| 방식 | 정확도 | 확장성 | 복잡도 | 적합 규모 |
|------|--------|--------|--------|-----------|
| Redis INCR (단일) | 정확 | 낮음 | 낮음 | ~10만 |
| Kafka Offset 기반 | 근사치 | 높음 | 중간 | ~1억 |
| Redis Sorted Set | 정확 | 중간 | 낮음 | ~1000만 |
| **멀티 스테이지 (권장)** | 정확 | 높음 | 높음 | **1억+** |

---

## 멀티 스테이지 파이프라인 아키텍처

### 전체 구조

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           STAGE 1: 트래픽 분산                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Users (1억 명)                                                            │
│       │                                                                     │
│       ↓                                                                     │
│   ┌─────────┐                                                               │
│   │ Kafka   │ ─→ [P0][P1][P2][P3][P4][P5][P6][P7][P8][P9]                  │
│   │Producer │    (10 파티션으로 트래픽 분산)                                  │
│   └─────────┘                                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                        STAGE 2: 순번 부여 (Redis)                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Consumer Group A (10개)                                                   │
│   ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐   │
│   │C0  │ │C1  │ │C2  │ │C3  │ │C4  │ │C5  │ │C6  │ │C7  │ │C8  │ │C9  │   │
│   └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘   │
│      │      │      │      │      │      │      │      │      │      │      │
│      └──────┴──────┴──────┴──────┼──────┴──────┴──────┴──────┴──────┘      │
│                                  ↓                                          │
│                     ┌─────────────────────────┐                             │
│                     │     Redis Cluster       │                             │
│                     │  ┌─────────────────┐    │                             │
│                     │  │ INCR 글로벌 순번  │    │  ← 원자적 순번 부여          │
│                     │  │ ZADD 대기열      │    │  ← Sorted Set (순번=score)  │
│                     │  └─────────────────┘    │                             │
│                     └─────────────────────────┘                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                        STAGE 3: 입장 처리                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Queue Worker (Redis에서 순서대로 Pop)                                      │
│                                                                             │
│   ┌─────────────────────────────────────────┐                               │
│   │  while (true) {                         │                               │
│   │    // 순번 낮은 순서대로 N명씩 가져옴      │                               │
│   │    users = ZPOPMIN(queue, 1000)         │                               │
│   │    // 입장 처리                          │                               │
│   │    process(users)                       │                               │
│   │  }                                      │                               │
│   └─────────────────────────────────────────┘                               │
│                                  │                                          │
│                                  ↓                                          │
│              ┌───────────────────────────────────────┐                      │
│              │  대량 입장 시 → Kafka Topic 2         │                      │
│              │  (입장 처리용 별도 파이프라인)          │                      │
│              └───────────────────────────────────────┘                      │
│                                  │                                          │
│                                  ↓                                          │
│              ┌───────────────────────────────────────┐                      │
│              │  Consumer Group B                     │                      │
│              │  (실제 예약/결제/좌석배정 처리)         │                      │
│              └───────────────────────────────────────┘                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 각 Stage 역할

| Stage | 컴포넌트 | 역할 | 병목 해소 |
|-------|----------|------|----------|
| 1 | Kafka Producer | 트래픽 분산 | 10 파티션 → 10배 처리량 |
| 2 | Consumer + Redis | 글로벌 순번 부여 | Cluster → 수십만 TPS |
| 3 | Queue Worker | 순서대로 입장 허용 | 배치 처리 (1000명씩) |
| 4 | Kafka Consumer | 입장 후 처리 분산 | 20 Consumer → 병렬 처리 |

---

## 구현 코드

### Stage 1: Producer (트래픽 분산)

```java
@PostMapping("/queue/enter")
public QueueResponse enter(UUID userId, UUID eventId) {
    // Kafka 파티션 분산 (userId 기반)
    kafkaTemplate.send("queue-stage1",
        userId.toString(),  // 파티션 키
        new QueueRequest(userId, eventId, Instant.now())
    );

    return new QueueResponse("대기열 등록 중...");
}
```

### Stage 2: Consumer → Redis 순번 부여

```java
@KafkaListener(topics = "queue-stage1", concurrency = "10")
public void assignNumber(QueueRequest request) {
    // 글로벌 순번 (원자적)
    long number = redis.incr("queue:number:" + request.eventId());

    // Sorted Set에 추가 (score = 순번)
    redis.zadd(
        "queue:waiting:" + request.eventId(),
        number,  // score
        request.userId().toString()  // member
    );

    // 유저별 순번 저장 (조회용)
    redis.hset(
        "queue:user:" + request.eventId(),
        request.userId().toString(),
        String.valueOf(number)
    );
}
```

### Stage 3: Redis → 입장 처리

```java
@Scheduled(fixedRate = 1000)  // 1초마다
public void processQueue() {
    String eventId = "event-123";

    // 순번 낮은 순서대로 1000명씩 Pop
    Set<String> userIds = redis.zpopmin("queue:waiting:" + eventId, 1000);

    if (userIds.isEmpty()) return;

    // 대량 처리가 필요하면 → 다음 Kafka로
    for (String userId : userIds) {
        kafkaTemplate.send("queue-stage2-enter",
            new EnterRequest(UUID.fromString(userId), eventId)
        );
    }

    // 처리 중인 순번 업데이트
    redis.incrby("queue:processed:" + eventId, userIds.size());
}
```

### Stage 4: 최종 처리 (예약/결제)

```java
@KafkaListener(topics = "queue-stage2-enter", concurrency = "20")
public void processEnter(EnterRequest request) {
    // 실제 비즈니스 로직
    // - 좌석 배정
    // - 결제 처리
    // - 예약 확정
    reservationService.process(request);
}
```

---

## 순위 조회 API

```java
@GetMapping("/queue/status")
public QueueStatus getStatus(UUID userId, UUID eventId) {
    // 내 순번
    String myNumber = redis.hget("queue:user:" + eventId, userId.toString());

    // 처리된 수
    String processed = redis.get("queue:processed:" + eventId);

    if (myNumber == null) {
        return new QueueStatus("NOT_IN_QUEUE", 0, 0);
    }

    long number = Long.parseLong(myNumber);
    long done = processed != null ? Long.parseLong(processed) : 0;

    if (number <= done) {
        return new QueueStatus("YOUR_TURN", 0, number);  // 입장 가능
    }

    return new QueueStatus("WAITING", number - done, number);  // 대기 중
}
```

---

## Redis 데이터 구조

```
# 글로벌 순번 카운터
queue:number:{eventId} = AtomicLong (1, 2, 3, ...)

# 대기열 (Sorted Set)
queue:waiting:{eventId} = {
    userId1: 1 (score = 순번),
    userId2: 2,
    userId3: 3,
    ...
}

# 유저별 순번 (Hash)
queue:user:{eventId} = {
    userId1: "1",
    userId2: "2",
    ...
}

# 처리 완료 카운터
queue:processed:{eventId} = AtomicLong (처리된 수)
```

---

## 핵심 설계 원칙

### 1. 순번 부여와 처리 분리

```
❌ 잘못된 방식: Consumer에서 순번 부여 + 처리 동시에
   → Kafka 순서 ≠ 처리 순서 (Race Condition)

✅ 올바른 방식: 순번 부여 (Stage 2) → 순서대로 처리 (Stage 3)
   → Redis가 순서 보장
```

### 2. Kafka 순서 vs Redis 순서

```
Kafka Partition (순서 보장)
[1] → [2] → [3] → [4] → [5]
         ↓ Consumer 병렬 처리 시
Redis INCR (도착 순서)
[2] → [1] → [4] → [3] → [5]  ← 꼬임!
         ↓ Sorted Set으로 정렬
Redis Sorted Set
[1] → [2] → [3] → [4] → [5]  ← 정렬됨!
```

### 3. 배치 처리로 성능 확보

```java
// 1명씩 처리 (느림)
while (true) {
    user = redis.zpopmin("queue", 1);
    process(user);  // 1억 번 호출
}

// 1000명씩 배치 (빠름)
while (true) {
    users = redis.zpopmin("queue", 1000);
    kafkaTemplate.sendBatch("queue-enter", users);  // 10만 번 호출
}
```

---

## 처리량 계산

### 1억 명 / 10분 시나리오

| Stage | 처리량 | 계산 |
|-------|--------|------|
| Kafka (10 파티션) | ~100만 TPS | 10 파티션 × 10만/파티션 |
| Redis Cluster | ~50만 TPS | 6 노드 × 8만/노드 |
| Queue Worker | ~100만/분 | 1000명 × 60초 × 16 워커 |
| Final Consumer | ~20만 TPS | 20 Consumer × 1만/Consumer |

**병목 지점**: Stage 3 (Queue Worker) → 워커 수 증가로 해결

---

## 장애 대응

### Redis 장애

```java
try {
    rank = redis.incr("queue:number:" + eventId);
} catch (Exception e) {
    // Fallback: Kafka offset 기반 근사치
    rank = kafkaOffset;
    log.warn("Redis 장애, Kafka offset 사용: {}", rank);
}
```

### Kafka 장애

```java
@KafkaListener(...)
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void assignNumber(QueueRequest request) {
    // 재시도 로직
}
```

---

## 실제 서비스 사례

| 서비스 | 아키텍처 | 특징 |
|--------|----------|------|
| 인터파크 티켓 | Redis + Kafka | 대기열 + 좌석 배정 분리 |
| 네이버 예약 | Redis 기반 | Sorted Set 순번 관리 |
| 카카오 선착순 | Kafka + Redis | 멀티 스테이지 파이프라인 |
| 쿠팡 로켓배송 | Kafka Streams | 실시간 재고 처리 |

---

## 결론

1. **소규모 (10만 이하)**: Redis INCR 단일 구조로 충분
2. **중규모 (1000만 이하)**: Redis Cluster + Sorted Set
3. **대규모 (1억 이상)**: 멀티 스테이지 파이프라인 필수

**핵심**: "순번 부여"와 "처리"를 분리하고, Redis가 순서를 정렬하는 역할을 담당하게 하는 것.
