# Kafka 문서

## 개요

이 디렉토리는 Kafka 메시징 시스템의 보안, 설계, 구현에 관한 문서를 포함합니다.

---

## 문서 목록

### 1. [JSON 역직렬화 보안](./JSON_DESERIALIZATION_SECURITY.md)
**테스트 실패 원인과 보안 기초**

- 왜 테스트가 실패했는가?
- JSON 역직렬화 공격 원리
- `TRUSTED_PACKAGES` 설정
- Type Mapping 전략
- 보안 방어 전략 비교

**읽어야 할 사람:**
- Kafka 테스트 실패를 겪은 개발자
- JSON 역직렬화 보안을 이해하고 싶은 개발자
- Spring Kafka 설정을 처음 하는 개발자

---

### 2. [메시지 서명 구현](./MESSAGE_SIGNING.md)
**전체 패키지 허용 + 메시지 서명으로 보안 확보**

- RSA 서명/검증 원리
- Private/Public Key 생성
- Producer/Consumer 구현
- 키 관리 및 로테이션
- 성능 최적화

**읽어야 할 사람:**
- `TRUSTED_PACKAGES: "*"` 사용하면서 보안을 확보하고 싶은 개발자
- 외부 시스템과 연동하는 개발자
- 금융/보안 중요 시스템 개발자

---

### 3. [API Gateway 패턴](./API_GATEWAY_PATTERN.md)
**외부 입력을 신뢰 경계에서 차단**

- Zero Trust 원칙
- Defense in Depth (다층 방어)
- 인증/인가, Rate Limiting
- DTO 변환 (화이트리스트)
- 보안 사고 모니터링

**읽어야 할 사람:**
- 외부 API를 노출하는 개발자
- 멀티 테넌트 환경 개발자
- 공개 서비스 개발자

---

### 4. [String vs Object 선택 가이드](./PAYLOAD_SELECTION_GUIDE.md)
**Kafka 페이로드 설계 의사결정**

- 의사결정 트리
- 6가지 선택 기준
- 실전 예시 (사용자 등록, 주문 생성, 쿠폰 발급)
- 성능 비교
- 마이그레이션 가이드

**읽어야 할 사람:**
- Kafka 메시지 설계를 고민하는 개발자
- String vs Object 선택에 고민하는 개발자
- 성능 최적화가 필요한 개발자

---

## 읽는 순서

### 초급 (Kafka 처음 사용)
```
1. JSON 역직렬화 보안 (기초)
   ↓
2. String vs Object 선택 가이드 (설계)
```

### 중급 (프로덕션 준비)
```
1. JSON 역직렬화 보안 (기초)
   ↓
2. String vs Object 선택 가이드 (설계)
   ↓
3. 메시지 서명 구현 (보안 강화)
```

### 고급 (외부 API 노출)
```
1. JSON 역직렬화 보안 (기초)
   ↓
2. String vs Object 선택 가이드 (설계)
   ↓
3. 메시지 서명 구현 (보안 강화)
   ↓
4. API Gateway 패턴 (최고 보안)
```

---

## 빠른 참조

### 테스트 실패 해결
```java
// 문제: The class 'CouponIssueRequest' is not in the trusted packages
// 해결: TRUSTED_PACKAGES 설정
config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
```
→ [JSON 역직렬화 보안](./JSON_DESERIALIZATION_SECURITY.md#문제-발생-원인)

### String vs Object 선택
```
Consumer가 여러 필드 필요? → Object
마이크로서비스 (DB 분리)? → Object
외부 시스템 연동? → String
```
→ [String vs Object 선택 가이드](./PAYLOAD_SELECTION_GUIDE.md#의사결정-트리)

### 보안 강화
```java
// 메시지 서명
String signature = signer.sign(json);
record.headers().add("signature", signature.getBytes());
```
→ [메시지 서명 구현](./MESSAGE_SIGNING.md#구현)

### 외부 API 노출
```
외부 → API Gateway (검증) → Kafka (서명) → Consumer
```
→ [API Gateway 패턴](./API_GATEWAY_PATTERN.md#아키텍처)

---

## 보안 수준 비교

| 방법 | 보안 | 복잡도 | 성능 | 추천 환경 |
|------|------|--------|------|-----------|
| **TRUSTED_PACKAGES: "*"** | ⭐ | 낮음 | 높음 | 내부 시스템만 |
| **특정 패키지 허용** | ⭐⭐⭐ | 낮음 | 높음 | 일반적 |
| **Type Mapping** | ⭐⭐⭐⭐ | 낮음 | 높음 | 내부 시스템 |
| **메시지 서명** | ⭐⭐⭐⭐⭐ | 높음 | 중간 | 외부 연동 |
| **API Gateway** | ⭐⭐⭐⭐⭐ | 중간 | 중간 | 외부 API |

---

## 프로젝트 적용 현황

### 현재 구성
```
쿠폰 발급 시스템
├── Producer: CouponService
├── Consumer: CouponIssueConsumer
├── 메시지: CouponIssueRequest (Object)
└── 보안: TRUSTED_PACKAGES: "*"
```

### 적용된 패턴
- ✅ Object 전송 (3개 필드)
- ✅ TRUSTED_PACKAGES: "*" (내부 시스템)
- ⏳ 메시지 서명 (미적용)
- ⏳ API Gateway (미적용)

### 향후 계획
1. **단기**: 현재 구성 유지 (내부 시스템만 사용)
2. **중기**: 메시지 서명 적용 (외부 연동 준비)
3. **장기**: API Gateway 구축 (공개 API 노출)

---

## 관련 코드

### 설정
- `src/main/java/kr/hhplus/be/server/config/KafkaConsumer.java`
- `src/main/java/kr/hhplus/be/server/config/KafkaProducerConfig.java`

### Producer
- `src/main/java/kr/hhplus/be/server/application/coupon/CouponService.java`

### Consumer
- `src/main/java/kr/hhplus/be/server/infrastructure/kafka/CouponIssueConsumer.java`

### 메시지
- `src/main/java/kr/hhplus/be/server/application/coupon/CouponIssueRequest.java`

### 테스트
- `src/test/java/kr/hhplus/be/server/application/coupon/CouponIssueKafkaIntegrationTest.java`

---

## 참고 자료

### 공식 문서
- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/reference/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Jackson Security](https://github.com/FasterXML/jackson-docs/wiki/JacksonPolymorphicDeserialization)

### 보안
- [OWASP Deserialization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Deserialization_Cheat_Sheet.html)
- [CVE-2017-7525: Jackson Deserialization RCE](https://nvd.nist.gov/vuln/detail/CVE-2017-7525)

### 아키텍처
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)
- [Microservices Patterns](https://microservices.io/patterns/index.html)

---

## 기여

문서 개선 제안이나 오류 발견 시:
1. 이슈 생성
2. PR 제출
3. 팀 리뷰 후 반영

---

## 라이선스

이 문서는 프로젝트 내부용입니다.
