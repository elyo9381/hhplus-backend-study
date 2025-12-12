# 의존성 아키텍처 (Dependency Architecture)

## 문제 상황

Order/Payment는 클린 아키텍처, Product/Point는 레이어드 아키텍처로 구성되어 있어 의존성 방향이 일관되지 않았습니다.

### ❌ 이전 구조 (DIP 위반)

```
OrderService (Clean Architecture)
    ↓ (직접 의존 - 구체 클래스)
ProductService (Layered Architecture)

PaymentService (Clean Architecture)
    ↓ (직접 의존 - 구체 클래스)
PointService (Layered Architecture)
```

**문제점:**
- 고수준 모듈(OrderService, PaymentService)이 저수준 모듈(ProductService, PointService)에 직접 의존
- 의존성 역전 원칙(Dependency Inversion Principle) 위반
- 테스트 시 구체 클래스를 Mock해야 함
- 아키텍처 일관성 부족

## 해결 방법: Port 인터페이스 도입

### ✅ 개선된 구조 (DIP 준수)

```
┌─────────────────────────────────────────────────────────────┐
│                    Clean Architecture                        │
│  (Order Domain)                    (Payment Domain)          │
│                                                               │
│  ┌──────────────┐                 ┌──────────────┐          │
│  │ ProductPort  │◄────────────────│  PointPort   │          │
│  │ (Interface)  │                 │ (Interface)  │          │
│  └──────▲───────┘                 └──────▲───────┘          │
│         │                                │                   │
│         │ depends on                     │ depends on        │
│         │                                │                   │
│  ┌──────┴───────┐                 ┌──────┴───────┐          │
│  │ OrderService │                 │PaymentService│          │
│  └──────────────┘                 └──────────────┘          │
└─────────────────────────────────────────────────────────────┘
         ▲                                 ▲
         │ implements                      │ implements
         │                                 │
┌────────┴─────────────────────────────────┴──────────────────┐
│                  Layered Architecture                        │
│                                                               │
│  ┌──────────────┐                 ┌──────────────┐          │
│  │ProductService│                 │ PointService │          │
│  │ (Concrete)   │                 │  (Concrete)  │          │
│  └──────────────┘                 └──────────────┘          │
└───────────────────────────────────────────────────────────────┘
```

## 구현 상세

### 1. ProductPort 인터페이스 (Order 도메인)

```java
// Order 도메인에 정의 (의존성 역전)
package kr.hhplus.be.server.order.domain;

public interface ProductPort {
    ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity);
}
```

### 2. PointPort 인터페이스 (Payment 도메인)

```java
// Payment 도메인에 정의 (의존성 역전)
package kr.hhplus.be.server.payment.domain;

public interface PointPort {
    void usePoint(UUID userId, Long amount);
    Long getAvailablePoints(UUID userId);
}
```

### 3. OrderService (Port에 의존)

```java
@Service
public class OrderService {
    private final ProductPort productPort; // ← 인터페이스 의존
    
    public OrderService(OrderRepository orderRepository, 
                        ProductPort productPort) {
        this.orderRepository = orderRepository;
        this.productPort = productPort;
    }
    
    @Transactional
    public Order createOrder(UUID userId, List<OrderItemRequest> itemRequests) {
        // productPort 사용
        ProductSnapshot snapshot = productPort.decreaseStockWithSnapshot(
            request.productId(), 
            request.quantity()
        );
    }
}
```

### 4. PaymentService (Port에 의존)

```java
@Service
public class PaymentService {
    private final PointPort pointPort; // ← 인터페이스 의존
    
    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          PointPort pointPort) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.pointPort = pointPort;
    }
    
    @Transactional
    public Payment executePayment(UUID orderId, UUID userId) {
        // pointPort 사용
        pointPort.usePoint(userId, amount);
    }
}
```

### 5. ProductService (Port 구현)

```java
@Service
public class ProductService implements ProductPort {
    
    @Override
    @Transactional
    public ProductSnapshot decreaseStockWithSnapshot(UUID productId, int quantity) {
        // 구현
    }
}
```

### 6. PointService (Port 구현)

```java
@Service
public class PointService implements PointPort {
    
    @Override
    @Transactional
    public void usePoint(UUID userId, Long amount) {
        // 구현
    }
    
    @Override
    @Transactional(readOnly = true)
    public Long getAvailablePoints(UUID userId) {
        // 구현
    }
}
```

## 의존성 방향

### 핵심 원칙: 의존성 역전 (Dependency Inversion)

```
고수준 모듈 (OrderService, PaymentService)
    ↓ depends on
인터페이스 (ProductPort, PointPort) ← 도메인에 정의
    ↑ implements
저수준 모듈 (ProductService, PointService)
```

**의존성 흐름:**
1. OrderService → ProductPort (인터페이스)
2. ProductService → ProductPort (구현)
3. PaymentService → PointPort (인터페이스)
4. PointService → PointPort (구현)

**결과:**
- 고수준 모듈이 저수준 모듈에 의존하지 않음
- 인터페이스를 통한 느슨한 결합
- 테스트 시 Mock 객체 생성 용이

## 패키지 구조

```
kr.hhplus.be.server/
├── order/
│   ├── domain/
│   │   ├── Order.java
│   │   ├── OrderRepository.java
│   │   └── ProductPort.java          ← Port 인터페이스 (도메인에 정의)
│   ├── application/
│   │   └── OrderService.java         ← ProductPort에 의존
│   └── infrastructure/
│       └── OrderRepositoryImpl.java
│
├── payment/
│   ├── domain/
│   │   ├── Payment.java
│   │   ├── PaymentRepository.java
│   │   └── PointPort.java            ← Port 인터페이스 (도메인에 정의)
│   ├── application/
│   │   └── PaymentService.java       ← PointPort에 의존
│   └── infrastructure/
│       └── PaymentRepositoryImpl.java
│
├── product/
│   ├── ProductService.java           ← ProductPort 구현
│   ├── ProductEntity.java
│   └── ProductRepository.java
│
└── point/
    ├── PointService.java              ← PointPort 구현
    ├── PointEntity.java
    └── PointRepository.java
```

## 장점

### 1. 의존성 역전 원칙 준수
- 고수준 모듈이 저수준 모듈에 의존하지 않음
- 인터페이스를 통한 추상화

### 2. 테스트 용이성
```java
@Mock
private ProductPort productPort; // ← 인터페이스 Mock

@InjectMocks
private OrderService orderService;
```

### 3. 변경 영향 최소화
- ProductService 변경 시 OrderService 영향 없음
- 인터페이스만 유지하면 구현체 교체 가능

### 4. 아키텍처 일관성
- Order/Payment 도메인이 외부 의존성을 인터페이스로 정의
- 클린 아키텍처 원칙 준수

### 5. 확장성
- 새로운 구현체 추가 용이 (예: ExternalProductService)
- 분산 환경으로 전환 시 API 클라이언트로 교체 가능

## Spring DI 동작

Spring이 자동으로 의존성 주입:

```java
// Spring Container
ProductService implements ProductPort
    ↓ 자동 주입
OrderService(ProductPort productPort)
```

**동작 원리:**
1. ProductService가 ProductPort를 구현
2. Spring이 ProductService를 빈으로 등록
3. OrderService가 ProductPort 타입 요청
4. Spring이 ProductService를 주입 (다형성)

## 관련 ADR

- **ADR-003**: 인터페이스 기반 설계로 확장 가능한 코드베이스 구축
- **ADR-001**: DDD 레이어 구조로 역할과 책임 분리
- **ADR-002**: 모놀리식 환경이지만 향후 분산 환경 대비

## 결론

Port 인터페이스 도입으로:
- ✅ 의존성 역전 원칙(DIP) 준수
- ✅ 클린 아키텍처와 레이어드 아키텍처 통합
- ✅ 테스트 용이성 향상
- ✅ 변경 영향 최소화
- ✅ 아키텍처 일관성 확보

**핵심:** 고수준 모듈(도메인)이 인터페이스를 정의하고, 저수준 모듈(인프라)이 구현하는 의존성 역전을 달성했습니다.
