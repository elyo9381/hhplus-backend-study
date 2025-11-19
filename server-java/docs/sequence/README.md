# Business Sequence Diagrams

이 디렉토리는 HHPlus E-Commerce 시스템의 비즈니스 플로우를 Mermaid 시퀀스 다이어그램으로 표현합니다.

## 문서 목록

### 1. [Authentication (인증)](./01-auth.md)
- 회원가입 (Signup)
- 로그인 (Login)
- 사용자 조회 (Get User)

**주요 특징:**
- 이메일 중복 검증
- 비밀번호 해싱
- JWT 토큰 발급
- Redis 세션 관리

---

### 2. [Product (상품)](./02-product.md)
- 상품 목록 조회 (Get Products)
- 상품 상세 조회 (Get Product Detail)
- 인기 상품 조회 (Get Popular Products)

**주요 특징:**
- Redis 캐싱 (Cache Hit/Miss)
- 페이지네이션
- 최근 3일 주문 기반 인기 상품

---

### 3. [Order (주문)](./03-order.md)
- 주문 생성 (Create Order)
- 주문 조회 (Get Order)
- 주문 목록 조회 (Get Orders)

**주요 특징:**
- 재고 예약 (비관적 락)
- 쿠폰 검증 및 예약
- 금액 계산 (할인 적용)
- 트랜잭션 관리

---

### 4. [Payment (결제)](./04-payment.md)
- 결제 처리 (Process Payment)
- 환불 처리 (Process Refund)
- 결제 내역 조회 (Get Payment History)

**주요 특징:**
- 주문 상태 검증
- 포인트 사용 처리
- 외부 결제 게이트웨이 연동
- 쿠폰 사용 확정
- 비동기 로그 전송
- 환불 시 재고/포인트/쿠폰 복구

---

### 5. [Point (포인트)](./05-point.md)
- 포인트 충전 (Charge Points)
- 포인트 잔액 조회 (Get Point Balance)
- 포인트 사용 내역 조회 (Get Point History)
- 만료 예정 포인트 조회 (Get Expiring Points)

**주요 특징:**
- 만료일 관리 (1년)
- 잔액 캐싱 (Redis)
- 만료 예정 포인트 알림
- 포인트 이력 추적

---

### 6. [Coupon (쿠폰)](./06-coupon.md)
- 쿠폰 발급 (Issue Coupon)
- 사용 가능한 쿠폰 조회 (Get Available Coupons)
- 내 쿠폰 조회 (Get My Coupons)
- 주문에 적용 가능한 쿠폰 조회 (Get Applicable Coupons)
- 선착순 쿠폰 발급 (First-Come Coupon with Redis)

**주요 특징:**
- 중복 발급 방지
- 수량 제한 (비관적 락)
- 선착순 쿠폰 (Redis INCR)
- 쿠폰 적용 타입 (ORDER/PRODUCT)
- 최소 주문 금액 검증

---

## 다이어그램 표기법

### 참여자 (Participants)
- **User**: 최종 사용자
- **API Gateway**: REST API 엔드포인트
- **Service**: 비즈니스 로직 처리 (Application Layer)
- **Domain**: 도메인 로직 (Domain Layer)
- **DB (MySQL)**: 데이터베이스
- **Cache (Redis)**: 캐시 서버
- **LogServer**: 외부 로그 서버

### 주요 패턴

#### 1. 트랜잭션 관리
```
Note over Service: Transaction Start
... operations ...
Note over Service: Transaction Commit
```

#### 2. 비관적 락
```
Service->>DB: SELECT * FROM table WHERE id = ? FOR UPDATE
```

#### 3. 캐시 패턴
```
alt Cache Hit
    Cache-->>Service: Cached data
else Cache Miss
    Cache-->>Service: null
    Service->>DB: Query data
    Service->>Cache: SET cache
end
```

#### 4. 에러 처리
```
alt Error condition
    Service-->>API: 400 Bad Request
    API-->>User: Error message
else Success
    Service-->>API: 200 OK
    API-->>User: Response data
end
```

#### 5. 비동기 처리
```
Service->>LogServer: sendLog()
Note over Service,LogServer: Async, non-blocking
```

---

## 동시성 제어 전략

### 1. 재고 관리 (Product.stock)
- **비관적 락**: `SELECT ... FOR UPDATE`
- **WHERE 조건**: `UPDATE ... WHERE stock >= ?` (음수 방지)
- **데드락 방지**: 상품 ID 정렬 후 순차 락 획득
- **캐시 무효화**: 재고 변경 시 캐시 삭제
- **트랜잭션**: 재고 차감 + 주문 생성 원자성 보장

### 2. 쿠폰 발급 (Coupon.issued_quantity)
- **일반 쿠폰**: DB 비관적 락
- **선착순 쿠폰**: Redis INCR (원자적 연산)
- **중복 발급 방지**: UNIQUE 제약 조건
- **보상 트랜잭션**: DB 실패 시 Redis DECR로 롤백

### 3. 포인트 사용 (UserPoint.amount)
- **2단계 검증**:
  1. 사전 검증 (트랜잭션 밖): 빠른 실패
  2. 실제 차감 (트랜잭션 안): FOR UPDATE로 재확인
- **여러 레코드 차감**: 반복문으로 만료일 순 차감 (FIFO)
- **선입선출**: 만료일 빠른 순서로 차감
- **잔액 캐싱**: 조회 성능 향상

### 4. 주문 금액 계산
- **주문 생성 시**: 쿠폰 적용 후 금액 확정
  - `total_amount`, `discount_amount`, `final_amount` 저장
- **결제 시**: ORDER에서 금액 조회 후 포인트 차감
  - `paid_amount`, `point_amount`, `remaining_amount` 업데이트
- **환불 시**: ORDER에서 금액 조회 후 복구
  - `paid_amount`, `point_amount` 기반으로 정확한 환불
  - 재고 복구 시 FOR UPDATE로 동시성 제어

### 5. 데드락 방지
- **락 순서 정렬**: 상품 ID 오름차순으로 정렬 후 락 획득
- **일관된 순서**: 모든 트랜잭션이 동일한 순서로 락 획득
- **효과**: 데드락 발생 원천 차단

---

## 에러 처리 전략

### HTTP Status Codes
- **200 OK**: 성공
- **201 Created**: 리소스 생성 성공
- **400 Bad Request**: 잘못된 요청 (검증 실패)
- **401 Unauthorized**: 인증 실패
- **404 Not Found**: 리소스 없음
- **409 Conflict**: 충돌 (중복, 재고 부족 등)
- **500 Internal Server Error**: 서버 오류

### 트랜잭션 롤백
- 비즈니스 규칙 위반 시 자동 롤백
- 외부 API 실패 시 롤백
- 데이터 정합성 보장

---

## 성능 최적화

### 1. 캐싱 전략
- **상품 목록/상세**: 5분 TTL
- **포인트 잔액**: 1분 TTL
- **인기 상품**: 1시간 TTL

### 2. 쿼리 최적화
- **인덱스 활용**: WHERE, JOIN 조건에 인덱스
- **페이지네이션**: LIMIT/OFFSET
- **집계 쿼리**: COUNT, SUM 최소화

### 3. 비동기 처리
- **로그 전송**: 비동기, 논블로킹
- **알림 발송**: 이벤트 기반 처리

---

## 참고 문서

- [API Specification](../api-spec/openai.yaml)
- [ERD](../erd/ERD.md)
- [Infrastructure](../infra/infrastructure.md)
