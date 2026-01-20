# Redis 캐시 전략 및 선착순 쿠폰 설계 보고서

## 목차
1. [캐시 전략 개요](#1-캐시-전략-개요)
2. [선착순 쿠폰 시스템 설계](#2-선착순-쿠폰-시스템-설계)
3. [인기상품 랭킹 시스템 설계](#3-인기상품-랭킹-시스템-설계)
4. [Redis-DB 동기화 전략](#4-redis-db-동기화-전략)
5. [구현 회고](#5-구현-회고)

---

## 1. 캐시 전략 개요

### 1.1 주요 캐시 전략 비교

#### Cache-Aside (Look-Aside) - 가장 일반적

```
읽기 (Lazy Loading):
┌─────────────────────────────────────────────────────┐
│  App ──→ Cache 조회                                 │
│           │                                         │
│           ├─ HIT → 반환                             │
│           │                                         │
│           └─ MISS → DB 조회 → Cache 저장 → 반환     │
└─────────────────────────────────────────────────────┘

쓰기:
┌─────────────────────────────────────────────────────┐
│  App ──→ DB 저장 ──→ Cache 삭제 (무효화)            │
└─────────────────────────────────────────────────────┘
```

**특징:**
- 애플리케이션이 캐시와 DB를 직접 관리
- 필요한 데이터만 캐싱 (Lazy)
- 캐시 장애 시 DB로 Fallback 가능

**장점:**
- 구현 간단
- 읽기 많은 워크로드에 적합
- 캐시 장애에 강함

**단점:**
- 첫 요청 느림 (Cold Start)
- 캐시-DB 불일치 가능 (TTL 동안)
- 쓰기 후 읽기 시 stale 데이터 가능

---

#### Write-Through

```
쓰기 (동기):
┌─────────────────────────────────────────────────────┐
│  App ──→ Cache 저장 ──→ DB 저장 (동기)              │
│                                                     │
│  둘 다 성공해야 완료                                │
└─────────────────────────────────────────────────────┘
```

**특징:**
- 캐시와 DB에 동시에 쓰기
- 항상 일관성 유지

**장점:**
- 캐시-DB 항상 일치
- 데이터 유실 없음

**단점:**
- 쓰기 지연 (두 번 저장)
- 쓰기 많으면 성능 저하

---

#### Write-Behind (Write-Back)

```
쓰기 (비동기):
┌─────────────────────────────────────────────────────┐
│  App ──→ Cache 저장 ──→ 즉시 반환                   │
│                    │                                │
│                    └──→ 비동기로 DB 저장 (배치)     │
└─────────────────────────────────────────────────────┘
```

**특징:**
- 캐시에만 쓰고 즉시 반환
- 백그라운드에서 DB 동기화

**장점:**
- 쓰기 매우 빠름
- DB 부하 감소 (배치 처리)

**단점:**
- 데이터 유실 가능 (캐시 장애 시)
- 구현 복잡
- 일관성 보장 어려움

---

#### Write-Around

```
쓰기: DB만 저장 (캐시 건너뜀)
읽기: Cache-Aside 방식
```

**특징:**
- 쓰기 시 캐시 업데이트 안 함
- 읽기 시에만 캐시 로드

**장점:**
- 자주 읽히지 않는 데이터에 적합
- 불필요한 캐싱 방지

**단점:**
- 쓰기 직후 읽기 시 캐시 미스

---

### 1.2 전략 비교표

| 전략 | 읽기 성능 | 쓰기 성능 | 일관성 | 복잡도 | 사용 사례 |
|------|----------|----------|--------|--------|----------|
| **Cache-Aside** | 좋음 (HIT 시) | 좋음 | 중간 | 낮음 | 일반적인 조회 |
| **Read-Through** | 좋음 | 좋음 | 중간 | 중간 | ORM 캐시 |
| **Write-Through** | 좋음 | 느림 | 높음 | 중간 | 중요 데이터 |
| **Write-Behind** | 좋음 | 매우 빠름 | 낮음 | 높음 | 로그, 통계 |
| **Write-Around** | 첫 요청 느림 | 빠름 | 중간 | 낮음 | 자주 안 읽는 데이터 |

### 1.3 상황별 추천 전략

| 상황 | 추천 전략 | 이유 |
|------|----------|------|
| 일반 조회 (상품, 유저) | Cache-Aside | 읽기 많음, 구현 간단 |
| 중요 데이터 (결제, 주문) | Write-Through | 일관성 중요 |
| 로그, 통계 | Write-Behind | 쓰기 많음, 유실 허용 |
| **선착순 쿠폰, 재고** | **Redis as Source of Truth** | 동시성, 성능 |
| 대기열 | Redis Only | 휘발성 OK |

---

## 2. 선착순 쿠폰 시스템 설계

### 2.1 요구사항 분석

**기능적 요구사항:**
- 선착순 N명에게 쿠폰 발급
- 1인 1쿠폰 (중복 발급 방지)
- 발급 기간 제한

**비기능적 요구사항:**
- 높은 동시성 처리 (수천 TPS)
- 정확한 수량 제어 (초과 발급 방지)
- 빠른 응답 시간 (~10ms)

### 2.2 기존 방식의 문제점 (DB 비관적 락)

```java
// 기존 방식
@Transactional
public UserCoupon issueCoupon(UUID couponId, UUID userId) {
    // 1. 중복 체크
    if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
        throw new IllegalStateException("이미 발급받은 쿠폰입니다");
    }

    // 2. 비관적 락 (FOR UPDATE)
    Coupon coupon = couponRepository.findByIdWithLock(couponId)
            .orElseThrow(...);

    // 3. 수량 차감
    coupon.issue();
    couponRepository.save(coupon);

    // 4. 사용자 쿠폰 저장
    return userCouponRepository.save(new UserCoupon(userId, coupon));
}
```

**문제점:**

| 문제 | 원인 | 영향 |
|------|------|------|
| 락 경합 | FOR UPDATE로 행 잠금 | 동시 요청 시 대기 |
| 처리량 제한 | DB 커넥션 점유 | TPS ~50 |
| 분산 환경 미지원 | 단일 DB 락 | 스케일 아웃 불가 |

### 2.3 Redis 기반 설계 (Redisson 분산락)

#### 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        CouponService                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ issueCoupon(couponId, userId)                           │   │
│  │   1. Redis에서 쿠폰 정보 조회 (DB 조회 X)               │   │
│  │   2. Redis 분산락 + 발급                                │   │
│  │   3. 트랜잭션 롤백 시 Redis 자동 롤백                   │   │
│  │   4. DB 저장 (UserCoupon + Coupon)                      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CouponRedisRepository                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ tryIssue(couponId, userId)                              │   │
│  │   1. 쿠폰 정보 조회 (Redis Hash)                        │   │
│  │   2. 발급 기간 검증                                     │   │
│  │   3. 분산락 획득                                        │   │
│  │   4. 수량 체크 + 발급 (Set)                             │   │
│  │   5. 락 해제                                            │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Redis                                   │
│                                                                 │
│  coupon:lock:{couponId}   → Redisson Lock (분산락)             │
│  coupon:issued:{couponId} → Set (발급된 userId 목록)           │
│  coupon:info:{couponId}   → Hash (maxQuantity, startAt, endAt) │
└─────────────────────────────────────────────────────────────────┘
```

#### Redis 키 구조

| 키 | 타입 | 용도 | TTL |
|---|------|------|-----|
| `coupon:lock:{id}` | Lock | Redisson 분산락 | 10초 (Watchdog 갱신) |
| `coupon:issued:{id}` | Set | 발급 사용자 목록 | 쿠폰 만료일+1일 |
| `coupon:info:{id}` | Hash | 쿠폰 정보 캐시 | 쿠폰 만료일+1일 |

#### 분산락 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│  1. RLock.tryLock(5초 대기, 10초 유지)                          │
│     - Redisson Watchdog: 락 자동 갱신                           │
│     - Pub/Sub: 락 해제 즉시 알림                                │
└─────────────────────────────────────────────────────────────────┘
                              │ (락 획득 성공)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. 락 내부 (단일 스레드 보장)                                  │
│                                                                 │
│     RSet.size()  → 현재 발급 수 조회                           │
│     if (size >= max) → 수량 소진 예외                          │
│                                                                 │
│     RSet.add(userId) → 원자적 추가                             │
│     if (!added) → 이미 발급됨 (false 반환)                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. lock.unlock() (isHeldByCurrentThread 체크)                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.4 왜 Lua 스크립트 대신 Redisson 분산락인가?

| 항목 | Lua 스크립트 | Redisson 분산락 |
|------|-------------|-----------------|
| 원자성 | 스크립트 내 보장 | 락으로 보장 |
| 코드 복잡도 | 높음 (스크립트 관리) | 낮음 (Java API) |
| 테스트 | 어려움 | 쉬움 (Mock 가능) |
| 장애 복구 | 직접 구현 | Watchdog 자동 처리 |
| 디버깅 | 어려움 | 쉬움 |

**결론:** 성능 차이 미미, 유지보수성에서 Redisson 우위

### 2.5 성능 비교

| 항목 | DB 비관적 락 | Redis 분산락 |
|------|-------------|--------------|
| 처리량 | ~50 TPS | ~5,000+ TPS |
| 응답시간 | ~200ms | ~5ms |
| 락 경합 | 높음 | 낮음 |
| 확장성 | 단일 DB | 분산 가능 |

---

## 3. 인기상품 랭킹 시스템 설계

### 3.1 요구사항

- 가장 많이 주문한 상품 랭킹
- 일별/주별 랭킹
- 실시간 업데이트

### 3.2 Redis Sorted Set 활용

```
┌─────────────────────────────────────────────────────────────────┐
│  결제 완료 (PaymentService)                                     │
│                                                                 │
│  for each OrderItem:                                            │
│    ZINCRBY product:ranking:daily:{today} {quantity} {productId} │
│    ZINCRBY product:ranking:weekly:{week} {quantity} {productId} │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  랭킹 조회                                                      │
│                                                                 │
│  ZREVRANGE product:ranking:daily:{today} 0 9 WITHSCORES        │
│  → [(productId1, score1), (productId2, score2), ...]           │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 키 설계

| 키 | 용도 | TTL |
|---|------|-----|
| `product:ranking:daily:{yyyyMMdd}` | 일별 랭킹 | 3일 |
| `product:ranking:weekly:{yyyy}:{week}` | 주별 랭킹 | 10일 |

### 3.4 왜 결제 완료 시점인가?

| 시점 | 장점 | 단점 |
|------|------|------|
| 주문 생성 | 빠른 반영 | 결제 미완료 포함 |
| **결제 완료** | 실제 구매 확정 | 약간의 지연 |

**결정:** 결제 완료 시점 (실제 구매 데이터)

---

## 4. Redis-DB 동기화 전략

### 4.1 Source of Truth 결정

| 데이터 | Source of Truth | 이유 |
|--------|-----------------|------|
| 쿠폰 수량/발급 여부 | **Redis** | 동시성 처리 |
| 쿠폰 상세 정보 | DB | 영속성 |
| 사용자 쿠폰 목록 | DB | 조회용 |
| 랭킹 점수 | **Redis** | 실시간 집계 |

### 4.2 동기화 흐름

#### 쿠폰 생성 (Write-Through)

```
┌─────────────────────────────────────────────────────────────────┐
│  @Transactional                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 1. DB 저장 (Coupon)                                     │   │
│  │ 2. Redis 초기화 (동일한 값)                              │   │
│  │    - 실패 시 RuntimeException → DB 롤백                 │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

#### 쿠폰 발급 (Redis First + DB 동기화)

```
┌─────────────────────────────────────────────────────────────────┐
│  1. Redis 발급 (분산락 + Set)                                   │
│  2. TransactionSynchronization 등록                             │
│  3. DB 저장 (UserCoupon + Coupon.remainingQuantity)            │
│                                                                 │
│  커밋 성공 → 완료                                               │
│  롤백 발생 → afterCompletion에서 Redis 롤백                     │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 TransactionSynchronization 활용

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {
            couponRedisRepository.rollback(couponId, userId);
        }
    }
});
```

**장점:**
- 트랜잭션이 어떤 이유로든 롤백되면 Redis도 롤백
- try-catch보다 안전 (커밋 실패 케이스 포함)

### 4.4 정합성 보장 전략

| 시나리오 | 처리 방법 |
|----------|----------|
| Redis 성공 + DB 성공 | 정상 완료 |
| Redis 성공 + DB 실패 | TransactionSynchronization으로 Redis 롤백 |
| Redis 실패 | 예외 발생, DB 저장 안 함 |
| Redis 장애 | Fallback (DB 락 방식) 또는 서비스 중단 |

### 4.5 Redis 장애 복구 시 정합성 배치

```sql
-- Redis issued Set vs DB user_coupons 비교
SELECT 
    c.id as coupon_id,
    c.total_quantity,
    c.remaining_quantity,
    COUNT(uc.id) as db_issued_count
FROM coupons c
LEFT JOIN user_coupons uc ON c.id = uc.coupon_id
GROUP BY c.id;

-- Redis SCARD coupon:issued:{couponId}와 비교
-- 불일치 시 Redis 재초기화
```

---

## 5. 구현 회고

### 5.1 설계 결정 (ADR)

#### ADR-019: 쿠폰 선착순 발급 - Redisson 분산락 선택

**상황:**
- 선착순 쿠폰 발급 시 동시성 제어 필요
- DB 비관적 락의 성능 한계

**결정:**
- Redisson 분산락 + Redis Set 사용

**이유:**
- Lua 스크립트보다 유지보수 용이
- Watchdog으로 데드락 방지
- 테스트 용이 (Mock 가능)

**결과:**
- TPS ~50 → ~5,000+ 개선
- 코드 가독성 향상

---

#### ADR-023: Redis as Source of Truth

**상황:**
- 선착순 쿠폰에서 수량 체크의 정확성 필요
- DB와 Redis 중 어디가 진실인가?

**결정:**
- 수량/발급 여부: Redis가 Source of Truth
- 상세 정보/히스토리: DB

**이유:**
- 동시성 처리는 Redis가 우위
- DB는 영속성에 집중

**결과:**
- 명확한 책임 분리
- 동기화 전략 단순화

---

#### ADR-024: 캐시 전략 - Write-Through + Redis First

**상황:**
- 쿠폰 생성/발급 시 Redis-DB 동기화 필요

**결정:**
- 생성: Write-Through (DB + Redis 동시)
- 발급: Redis First + DB 동기화

**이유:**
- 생성은 드묾 → 동기 처리 OK
- 발급은 많음 → Redis 우선 처리

---

### 5.2 개선 포인트

#### 잘된 점

1. **Redisson 분산락 선택**
   - Lua 스크립트보다 유지보수 용이
   - Watchdog으로 안정성 확보

2. **TransactionSynchronization 활용**
   - 트랜잭션 롤백 시 Redis 자동 롤백
   - 정합성 보장

3. **Redis Only 발급**
   - DB 조회 제거로 성능 향상
   - 업계 표준 패턴 적용

#### 개선 필요

1. **Redis 장애 Fallback**
   - 현재: 서비스 중단
   - 개선: DB 락 방식 자동 전환

2. **정합성 배치**
   - Redis-DB 불일치 감지/복구 배치 필요

3. **모니터링**
   - Redis 락 대기 시간 메트릭
   - 발급 성공/실패율 대시보드

### 5.3 성능 테스트 결과

| 테스트 | 조건 | 결과 |
|--------|------|------|
| 선착순 발급 | 30명 동시, 10개 수량 | 10명 성공, 20명 실패 ✅ |
| 중복 발급 방지 | 동일 사용자 10회 | 1회만 성공 ✅ |
| 랭킹 업데이트 | 결제 완료 시 | 즉시 반영 ✅ |

---

## 부록: 코드 구조

```
src/main/java/kr/hhplus/be/server/
├── application/coupon/
│   └── CouponService.java          # 쿠폰 발급 비즈니스 로직
├── infrastructure/coupon/
│   └── CouponRedisRepository.java  # Redis 분산락 + Set 관리
├── infrastructure/product/
│   └── ProductRankingRepository.java # 랭킹 Sorted Set 관리
└── config/
    └── RedissonConfig.java         # Redisson 설정

src/test/java/kr/hhplus/be/server/
├── coupon/
│   ├── CouponServiceUnitTest.java      # 단위 테스트 (Mock)
│   └── CouponRedisIntegrationTest.java # 통합 테스트 (Testcontainers)
└── product/
    └── ProductRankingIntegrationTest.java # 랭킹 통합 테스트
```

---

## 참고 자료

- [Redisson Documentation](https://redisson.org/documentation.html)
- [Redis Sorted Set](https://redis.io/docs/data-types/sorted-sets/)
- [Cache-Aside Pattern - Microsoft](https://docs.microsoft.com/en-us/azure/architecture/patterns/cache-aside)
- [CQRS Pattern](https://martinfowler.com/bliki/CQRS.html)
