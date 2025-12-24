# e-커머스 과제 구현 진행 기록

## 날짜: 2025-12-24

---

## 1. 프로젝트 분석 및 아키텍처 리팩토링

### 1.1 Port-Adapter 패턴 문제 발견

**문제점:**
- `infrastructure/outbox` 디렉토리에 Primary Adapter와 Secondary Adapter가 혼재
- OutboxScheduler (Primary - 애플리케이션 호출)와 MessageProducer (Secondary - 애플리케이션이 사용)가 같은 위치

**해결:**
```
Before:
infrastructure/outbox/
├── scheduler/OutboxScheduler    ← Primary Adapter
├── message/                      ← Secondary Adapter
└── persistence/                  ← Secondary Adapter

After:
presentation/scheduler/
└── OutboxScheduler              ← Primary Adapter (트리거 역할만)

application/outbox/
├── MessageProducer              ← Port (인터페이스)
└── OutboxPublisher              ← UseCase (비즈니스 로직)

infrastructure/outbox/
├── message/                     ← Secondary Adapter
└── persistence/                 ← Secondary Adapter
```

**핵심 인사이트:**
- "기능별 그룹핑"이 아닌 "방향별 그룹핑" 필요
- Primary Adapter: 애플리케이션을 "호출"
- Secondary Adapter: 애플리케이션이 "사용"
- Scheduler는 기술처럼 보이지만 역할은 Controller와 동일

**커밋:** 리팩토링 완료

---

## 2. idempotency_key 개념 학습

### 2.1 왜 필요한가?

**문제 상황:**
```
클라이언트: 결제 요청 전송
→ 네트워크 타임아웃 💥
→ 클라이언트: "성공? 실패? 모름"
→ 재시도 필요
```

**orderId만으로는 부족:**
- 같은 주문에 대한 "재시도"인지 "추가 결제"인지 구분 불가
- 부분 결제 시나리오 처리 불가

### 2.2 idempotency_key의 역할

**클라이언트가 생성:**
```javascript
const idempotencyKey = uuidv4(); // 요청 시작 시 생성

// 첫 번째 시도
fetch('/payments', {
  body: {
    orderId: 'order-123',
    idempotencyKey: 'idem-abc-123'  // ← 클라이언트가 생성
  }
});

// 타임아웃 → 재시도 (동일한 key)
fetch('/payments', {
  body: {
    orderId: 'order-123',
    idempotencyKey: 'idem-abc-123'  // ← 동일한 key
  }
});
```

**서버 처리:**
```java
@Transactional
public Payment executePayment(PaymentRequest request) {
    // 1. idempotency_key로 기존 결제 조회
    Optional<Payment> existing = paymentRepository
        .findByIdempotencyKey(request.getIdempotencyKey());
    
    if (existing.isPresent()) {
        return existing.get();  // 기존 결과 반환
    }
    
    // 2. 신규 결제 처리
    Payment payment = new Payment(..., request.getIdempotencyKey());
    return paymentRepository.save(payment);
}
```

### 2.3 orderId vs idempotencyKey

| 구분 | orderId | idempotencyKey |
|------|---------|----------------|
| 생성 주체 | 서버 (주문 생성 시) | 클라이언트 (결제 요청 시) |
| 목적 | 주문 식별 | 요청 중복 방지 |
| 범위 | 주문 전체 | 특정 API 요청 |
| 재사용 | 여러 결제 가능 | 재사용 불가 |
| 의미 | "어떤 주문" | "어떤 요청" |

### 2.4 실제 서비스 사례

**모두 동일한 개념, 명칭만 다름:**
- Stripe: `Idempotency-Key`
- AWS: `ClientToken`
- PayPal: `PayPal-Request-Id`
- Google: `request-id`
- Shopify: `idempotencyKey`

**핵심:**
> 클라이언트가 생성한 고유 식별자로 요청의 중복 처리를 방지

---

## 3. 과제 구현 플랜

### 3.1 현재 상태

**✅ 구현 완료:**
- Order, Payment, User, Outbox 패턴
- Port-Adapter 구조, Testcontainers
- WebClient 외부 API 연동

**❌ 미구현:**
- Product, Point, Coupon 도메인
- idempotency_key

### 3.2 구현 순서

**Phase 1: 핵심 도메인 (1-2일)**
1. Product 도메인 (재고 관리)
2. Point 도메인 (충전/사용)
3. Coupon 도메인 (선착순 발급)

**Phase 2: idempotency_key (0.5일)**
1. Payment에 idempotencyKey 필드 추가
2. PaymentService 중복 처리 로직

**Phase 3: 통합 테스트 (2-3일)**
1. 충전 → 주문 → 결제 플로우
2. 재고 동시성 테스트
3. 쿠폰 선착순 테스트
4. idempotency 중복 요청 테스트
5. Outbox 실패 시나리오

**Phase 4: 심화 과제 (2-3일)**
1. 성능 분석 보고서
2. 인덱스 설계
3. 동시성 이슈 리스트업

---

## 4. 구현 진행 상황

### 4.1 Product 도메인 모델 구현 ✅

**파일:**
- `Product.java`: 재고 관리 로직
  - `decreaseStock()`: 재고 차감
  - `increaseStock()`: 재고 증가
  - `validateStock()`: 재고 검증
- `ProductStatus.java`: SELLING, SOLDOUT
- `InsufficientStockException.java`: 재고 부족 예외
- `ProductRepository.java`: findByIdWithLock 포함

**비즈니스 규칙:**
- 재고 차감 시 0 이하 검증
- 재고 0이 되면 SOLDOUT 상태 변경
- 재고 증가 시 SELLING 상태 복구

**커밋:** `2cde8ac` - feat: Product 도메인 모델 구현

---

### 4.2 Product Infrastructure 구현 ✅

**파일:**
- `ProductEntity.java`: JPA 엔티티
  - Entity ↔ Domain 변환 메서드
  - `from()`, `toDomain()`
- `ProductJpaRepository.java`: Spring Data JPA
  - `@Lock(PESSIMISTIC_WRITE)` 비관적 락
  - `findByIdWithLock()`: SELECT ... FOR UPDATE
- `ProductRepositoryImpl.java`: Domain Repository 구현체

**기존 코드 수정:**
- `ProductService`: Entity → Domain 모델 사용
- `ProductController`: Entity → Domain 모델 사용
- `ProductResponse`: Entity → Domain 모델 사용
- `ProductRequest`: BigDecimal → Long 타입 변경

**비관적 락 적용:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM ProductEntity p WHERE p.id = :id")
Optional<ProductEntity> findByIdWithLock(@Param("id") UUID id);
```
→ `SELECT * FROM products WHERE id = ? FOR UPDATE`

**커밋:** `6ab9929` - feat: Product Infrastructure 구현

---

### 4.3 Product 단위 테스트 작성 (진행 중)

**파일:**
- `ProductTest.java`: 도메인 로직 테스트

**테스트 케이스:**
1. ✅ 상품 생성 시 재고가 있으면 SELLING 상태
2. ✅ 상품 생성 시 재고가 0이면 SOLDOUT 상태
3. ✅ 재고 차감 성공
4. ✅ 재고를 모두 차감하면 SOLDOUT 상태로 변경
5. ✅ 재고 부족 시 예외 발생
6. ✅ 0 이하 수량 차감 시 예외 발생
7. ✅ 재고 증가 성공
8. ✅ SOLDOUT 상태에서 재고 증가 시 SELLING 상태로 변경
9. ✅ 0 이하 수량 증가 시 예외 발생
10. ✅ 재고 검증 성공/실패

**상태:** 테스트 코드 작성 완료, 기존 테스트 파일 정리 필요

---

## 5. 다음 단계

### 5.1 즉시 진행
- [ ] Product 테스트 실행 및 커밋
- [ ] Point 도메인 모델 구현
- [ ] Point Infrastructure 구현
- [ ] Point 단위 테스트

### 5.2 이후 진행
- [ ] Coupon 도메인 (선착순 로직 포함)
- [ ] idempotency_key 구현
- [ ] 통합 테스트 작성
- [ ] 동시성 테스트 작성
- [ ] 성능 분석 및 문서화

---

## 6. 핵심 학습 내용

### 6.1 아키텍처 원칙

**헥사고날 아키텍처:**
- Primary Adapter: 애플리케이션을 호출 (Controller, Scheduler)
- Secondary Adapter: 애플리케이션이 사용 (Repository, MessageProducer)
- 방향별 그룹핑이 기능별 그룹핑보다 중요

**Port-Adapter 패턴:**
- Port: 인터페이스 (application 레이어)
- Adapter: 구현체 (infrastructure 레이어)
- 의존성 방향: Adapter → Port (안쪽으로)

### 6.2 도메인 설계

**Domain vs Entity:**
- Domain: 비즈니스 로직 포함 (순수 Java)
- Entity: 기술 구현체 (JPA 애노테이션)
- 변환 메서드로 분리 유지

**비관적 락:**
- 재고 차감 같은 동시성 이슈에 적용
- `@Lock(PESSIMISTIC_WRITE)`
- SELECT ... FOR UPDATE

### 6.3 멱등성 (Idempotency)

**핵심 개념:**
- 네트워크 불안정 환경에서 재시도 안전성 보장
- 클라이언트가 요청별 고유 키 생성
- 서버는 키로 중복 처리 방지

**동시성 제어와의 차이:**
- 동시성: 여러 요청이 동시에 같은 자원 접근
- 멱등성: 같은 요청을 여러 번 보내도 결과 동일

---

## 7. 참고 자료

### 7.1 프로젝트 문서
- `README.md`: 프로젝트 개요 및 ADR
- `docs/erd/ERD.md`: 데이터베이스 설계
- `docs/sequence/`: 시퀀스 다이어그램
- `API_SEPARATION_AND_OUTBOX.md`: Outbox 패턴
- `WEBCLIENT_EXTERNAL_API.md`: WebClient 구현

### 7.2 커밋 히스토리
- `2cde8ac`: Product 도메인 모델 구현
- `6ab9929`: Product Infrastructure 구현
- 이전: Outbox 패턴, WebClient, Testcontainers

---

## 8. TODO 체크리스트

**Infrastructure Layer:**
- [x] ProductRepository
- [ ] PointRepository, PointHistoryRepository
- [ ] CouponRepository, UserCouponRepository
- [ ] idempotency_key 추가

**통합 테스트:**
- [ ] 충전 → 주문 → 결제 플로우
- [ ] 재고 동시성 테스트
- [ ] 쿠폰 선착순 테스트
- [ ] idempotency 중복 요청 테스트
- [ ] Outbox 실패 시나리오 테스트

**심화 과제:**
- [ ] 성능 분석 보고서
- [ ] 인덱스 설계
- [ ] 동시성 이슈 리스트업
