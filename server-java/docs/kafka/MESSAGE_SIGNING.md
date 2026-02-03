# 메시지 서명 구현 가이드

## 개요

메시지 서명(Message Signing)은 **전체 패키지 허용(`TRUSTED_PACKAGES: "*"`)과 함께 사용하여 보안을 보장**하는 방법입니다.

### 핵심 아이디어
```
TRUSTED_PACKAGES: "*"  (유연성)
        +
메시지 서명/검증      (보안)
        =
안전하고 유연한 시스템
```

---

## 동작 원리

### 1. 암호화 기초

#### 비대칭 키 암호화 (RSA)
```
Private Key (개인키)  → 서명 생성
Public Key (공개키)   → 서명 검증

Private Key로 서명 → Public Key로만 검증 가능
Private Key 유출 안 되면 → 위조 불가능
```

#### 서명 프로세스
```
1. 메시지 해시 생성 (SHA-256)
   "Hello" → "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

2. Private Key로 해시 암호화 (서명)
   hash + privateKey → signature

3. 메시지 + 서명 전송
   message: "Hello"
   signature: "a3f5b2c..."

4. Public Key로 서명 검증
   signature + publicKey → hash 복호화
   message → hash 재생성
   두 hash 비교 → 일치하면 검증 성공
```

### 2. Kafka 메시지 서명 흐름

```
Producer (API Gateway)
    ↓
1. 메시지 생성
   CouponIssueRequest(couponId, userId)
    ↓
2. JSON 직렬화
   {"couponId": "...", "userId": "..."}
    ↓
3. 서명 생성 (Private Key)
   signature = sign(json, privateKey)
    ↓
4. Kafka 전송
   Headers: { "signature": "a3f5b2c..." }
   Body: {"couponId": "...", "userId": "..."}
    ↓
Kafka (저장)
    ↓
Consumer
    ↓
5. 서명 검증 (Public Key)
   if (!verify(json, signature, publicKey)) {
       throw SecurityException("Invalid signature");
   }
    ↓
6. 역직렬화 (TRUSTED_PACKAGES: "*")
   CouponIssueRequest request = objectMapper.readValue(json, CouponIssueRequest.class);
    ↓
7. 비즈니스 로직 처리
```

---

## 구현

### 1. 키 생성

#### RSA 키 페어 생성
```bash
# Private Key 생성 (2048bit)
openssl genrsa -out private_key.pem 2048

# Public Key 추출
openssl rsa -in private_key.pem -pubout -out public_key.pem

# PKCS8 형식으로 변환 (Java 호환)
openssl pkcs8 -topk8 -inform PEM -outform DER -in private_key.pem -out private_key.der -nocrypt
openssl rsa -in private_key.pem -pubout -outform DER -out public_key.der
```

#### 키 파일 위치
```
src/main/resources/keys/
├── private_key.der  (Producer만 사용 - 보안 중요!)
└── public_key.der   (Consumer 사용)
```

### 2. 서명 유틸 클래스

```java
package kr.hhplus.be.server.infrastructure.kafka;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class MessageSigner {
    
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    
    public MessageSigner() throws Exception {
        this.privateKey = loadPrivateKey();
        this.publicKey = loadPublicKey();
    }
    
    /**
     * 메시지 서명 생성
     * @param message 서명할 메시지
     * @return Base64 인코딩된 서명
     */
    public String sign(String message) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign message", e);
        }
    }
    
    /**
     * 서명 검증
     * @param message 원본 메시지
     * @param signatureStr Base64 인코딩된 서명
     * @return 검증 성공 여부
     */
    public boolean verify(String message, String signatureStr) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }
    
    private PrivateKey loadPrivateKey() throws Exception {
        ClassPathResource resource = new ClassPathResource("keys/private_key.der");
        byte[] keyBytes = resource.getInputStream().readAllBytes();
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }
    
    private PublicKey loadPublicKey() throws Exception {
        ClassPathResource resource = new ClassPathResource("keys/public_key.der");
        byte[] keyBytes = resource.getInputStream().readAllBytes();
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }
}
```

### 3. Producer 구현

```java
package kr.hhplus.be.server.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SecureKafkaProducer {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MessageSigner signer;
    private final ObjectMapper objectMapper;
    
    public SecureKafkaProducer(
        KafkaTemplate<String, String> kafkaTemplate,
        MessageSigner signer,
        ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 서명된 메시지 전송
     * @param topic Kafka 토픽
     * @param message 전송할 객체
     */
    public void sendSigned(String topic, Object message) {
        try {
            // 1. JSON 직렬화
            String json = objectMapper.writeValueAsString(message);
            
            // 2. 서명 생성
            String signature = signer.sign(json);
            
            // 3. ProducerRecord 생성 (헤더에 서명 추가)
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, json);
            record.headers().add("signature", signature.getBytes());
            
            // 4. 전송
            kafkaTemplate.send(record);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to send signed message", e);
        }
    }
    
    /**
     * 키와 함께 서명된 메시지 전송
     */
    public void sendSigned(String topic, String key, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            String signature = signer.sign(json);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
            record.headers().add("signature", signature.getBytes());
            
            kafkaTemplate.send(record);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to send signed message", e);
        }
    }
}
```

### 4. Consumer 구현

```java
package kr.hhplus.be.server.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.application.coupon.CouponIssueRequest;
import kr.hhplus.be.server.application.coupon.CouponService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class CouponIssueConsumer {
    
    private final CouponService couponService;
    private final MessageSigner signer;
    private final ObjectMapper objectMapper;
    
    public CouponIssueConsumer(
        CouponService couponService,
        MessageSigner signer,
        ObjectMapper objectMapper
    ) {
        this.couponService = couponService;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }
    
    @KafkaListener(topics = "coupon-issue-request", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
        @Payload String json,
        @Header(value = "signature", required = true) String signature
    ) {
        try {
            // 1. 서명 검증
            if (!signer.verify(json, signature)) {
                throw new SecurityException("Invalid message signature - possible attack detected");
            }
            
            // 2. 검증 성공 → 역직렬화 (TRUSTED_PACKAGES: "*" 사용)
            CouponIssueRequest request = objectMapper.readValue(json, CouponIssueRequest.class);
            
            // 3. 비즈니스 로직 처리
            couponService.issueCouponInternal(
                request.requestId(),
                request.couponId(),
                request.userId()
            );
            
        } catch (SecurityException e) {
            // 보안 예외 → 로깅 + 알림
            logSecurityIncident(json, signature, e);
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process message", e);
        }
    }
    
    private void logSecurityIncident(String json, String signature, Exception e) {
        // 보안 사고 로깅
        // - 메시지 내용
        // - 서명 값
        // - 시간
        // - IP (가능하면)
        // - 알림 발송 (Slack, Email 등)
    }
}
```

### 5. Service 레이어 수정

```java
@Service
public class CouponService {
    
    private final SecureKafkaProducer secureProducer;
    private final CouponIssueStatusRepository statusRepository;
    
    /**
     * 쿠폰 발급 요청 (비동기)
     */
    @Transactional
    public UUID issueCoupon(UUID couponId, UUID userId) {
        UUID requestId = UUID.randomUUID();
        
        // 1. 상태 저장 (PENDING)
        CouponIssueStatus status = new CouponIssueStatus(requestId, couponId, userId, PENDING);
        statusRepository.save(status);
        
        // 2. 서명된 메시지 전송
        CouponIssueRequest request = new CouponIssueRequest(requestId, couponId, userId);
        secureProducer.sendSigned("coupon-issue-request", request);
        
        return requestId;
    }
    
    /**
     * 실제 쿠폰 발급 로직 (Consumer에서 호출)
     */
    @Transactional
    public void issueCouponInternal(UUID requestId, UUID couponId, UUID userId) {
        // 실제 발급 로직
        // ...
    }
}
```

---

## 보안 고려사항

### 1. Private Key 보호

#### ❌ 절대 하지 말 것
```java
// 코드에 하드코딩
String privateKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASC...";

// Git에 커밋
git add src/main/resources/keys/private_key.pem
```

#### ✅ 올바른 방법

##### 개발 환경
```yaml
# application-dev.yml
security:
  message-signing:
    private-key-path: classpath:keys/private_key.der
    public-key-path: classpath:keys/public_key.der
```

##### 프로덕션 환경
```bash
# 환경 변수로 주입
export PRIVATE_KEY_PATH=/secure/vault/private_key.der
export PUBLIC_KEY_PATH=/secure/vault/public_key.der

# 또는 AWS Secrets Manager
aws secretsmanager get-secret-value --secret-id kafka-signing-key
```

##### Kubernetes Secret
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: kafka-signing-keys
type: Opaque
data:
  private_key.der: <base64-encoded-key>
  public_key.der: <base64-encoded-key>
```

```yaml
# Deployment
spec:
  containers:
  - name: app
    volumeMounts:
    - name: signing-keys
      mountPath: /app/keys
      readOnly: true
  volumes:
  - name: signing-keys
    secret:
      secretName: kafka-signing-keys
```

### 2. 키 로테이션

```java
@Component
public class MessageSigner {
    
    private final Map<String, PrivateKey> privateKeys = new ConcurrentHashMap<>();
    private final Map<String, PublicKey> publicKeys = new ConcurrentHashMap<>();
    private String currentKeyId;
    
    public String sign(String message) {
        String signature = signWithKey(message, currentKeyId);
        return currentKeyId + ":" + signature;  // 키 ID 포함
    }
    
    public boolean verify(String message, String signatureWithKeyId) {
        String[] parts = signatureWithKeyId.split(":");
        String keyId = parts[0];
        String signature = parts[1];
        
        PublicKey publicKey = publicKeys.get(keyId);
        if (publicKey == null) {
            return false;  // 만료된 키
        }
        
        return verifyWithKey(message, signature, publicKey);
    }
    
    /**
     * 새 키 추가 (무중단 로테이션)
     */
    public void addKey(String keyId, PrivateKey privateKey, PublicKey publicKey) {
        privateKeys.put(keyId, privateKey);
        publicKeys.put(keyId, publicKey);
        this.currentKeyId = keyId;  // 새 키로 전환
    }
    
    /**
     * 오래된 키 제거 (모든 메시지 처리 후)
     */
    public void removeKey(String keyId) {
        privateKeys.remove(keyId);
        // Public Key는 유지 (오래된 메시지 검증용)
        // 일정 기간 후 제거
    }
}
```

### 3. 성능 최적화

#### 서명 캐싱 (동일 메시지 반복 전송 시)
```java
@Component
public class CachedMessageSigner {
    
    private final MessageSigner signer;
    private final Cache<String, String> signatureCache;
    
    public CachedMessageSigner(MessageSigner signer) {
        this.signer = signer;
        this.signatureCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    }
    
    public String sign(String message) {
        return signatureCache.get(message, signer::sign);
    }
}
```

#### 비동기 서명 (대량 메시지)
```java
@Component
public class AsyncMessageSigner {
    
    private final MessageSigner signer;
    private final ExecutorService executor;
    
    public CompletableFuture<String> signAsync(String message) {
        return CompletableFuture.supplyAsync(() -> signer.sign(message), executor);
    }
}
```

---

## 테스트

### 1. 단위 테스트

```java
@SpringBootTest
class MessageSignerTest {
    
    @Autowired
    private MessageSigner signer;
    
    @Test
    void 서명_생성_및_검증_성공() {
        // given
        String message = "Hello, Kafka!";
        
        // when
        String signature = signer.sign(message);
        boolean isValid = signer.verify(message, signature);
        
        // then
        assertThat(isValid).isTrue();
    }
    
    @Test
    void 메시지_변조_시_검증_실패() {
        // given
        String originalMessage = "Hello, Kafka!";
        String signature = signer.sign(originalMessage);
        
        // when
        String tamperedMessage = "Hello, Hacker!";
        boolean isValid = signer.verify(tamperedMessage, signature);
        
        // then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void 서명_변조_시_검증_실패() {
        // given
        String message = "Hello, Kafka!";
        String signature = signer.sign(message);
        
        // when
        String tamperedSignature = signature.substring(0, signature.length() - 5) + "XXXXX";
        boolean isValid = signer.verify(message, tamperedSignature);
        
        // then
        assertThat(isValid).isFalse();
    }
}
```

### 2. 통합 테스트

```java
@SpringBootTest
@Testcontainers
class SecureKafkaIntegrationTest extends TestContainerSupport {
    
    @Autowired
    private SecureKafkaProducer producer;
    
    @Autowired
    private CouponIssueStatusRepository statusRepository;
    
    @Test
    void 서명된_메시지_전송_및_처리_성공() throws InterruptedException {
        // given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CouponIssueRequest request = new CouponIssueRequest(UUID.randomUUID(), couponId, userId);
        
        // when
        producer.sendSigned("coupon-issue-request", request);
        
        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            CouponIssueStatus status = statusRepository.findById(request.requestId()).orElseThrow();
            assertThat(status.getStatus()).isEqualTo(SUCCESS);
        });
    }
    
    @Test
    void 서명_없는_메시지_처리_실패() {
        // given
        String json = "{\"couponId\":\"...\",\"userId\":\"...\"}";
        
        // when
        ProducerRecord<String, String> record = new ProducerRecord<>("coupon-issue-request", json);
        // 서명 헤더 없음
        
        // then
        assertThatThrownBy(() -> kafkaTemplate.send(record).get())
            .hasCauseInstanceOf(SecurityException.class);
    }
}
```

---

## 모니터링

### 1. 서명 검증 실패 메트릭

```java
@Component
public class SecureKafkaConsumer {
    
    private final MeterRegistry meterRegistry;
    private final Counter signatureFailureCounter;
    
    public SecureKafkaConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.signatureFailureCounter = Counter.builder("kafka.signature.verification.failure")
            .description("Number of signature verification failures")
            .register(meterRegistry);
    }
    
    @KafkaListener(topics = "coupon-issue-request")
    public void consume(@Payload String json, @Header("signature") String signature) {
        if (!signer.verify(json, signature)) {
            signatureFailureCounter.increment();
            throw new SecurityException("Invalid signature");
        }
        // ...
    }
}
```

### 2. 알림 설정

```java
@Component
public class SecurityAlertService {
    
    private final SlackClient slackClient;
    
    public void alertSignatureFailure(String json, String signature, String topic) {
        String message = String.format(
            "🚨 *Kafka Security Alert*\n" +
            "Topic: %s\n" +
            "Reason: Invalid signature\n" +
            "Time: %s\n" +
            "Message: %s",
            topic,
            LocalDateTime.now(),
            json.substring(0, Math.min(100, json.length()))
        );
        
        slackClient.sendMessage("#security-alerts", message);
    }
}
```

---

## 장단점 분석

### 장점
1. ✅ **최고 수준의 보안**: 서명 위조 불가능
2. ✅ **유연성**: `TRUSTED_PACKAGES: "*"` 사용 가능
3. ✅ **무결성 보장**: 메시지 변조 감지
4. ✅ **부인 방지**: Private Key 소유자만 서명 가능

### 단점
1. ❌ **복잡도 증가**: 키 관리, 서명/검증 로직 필요
2. ❌ **성능 오버헤드**: RSA 서명/검증 비용 (메시지당 ~1ms)
3. ❌ **키 관리 부담**: 로테이션, 보안 저장소 필요

### 성능 비교

| 방법 | 처리 시간 (메시지당) | TPS (단일 스레드) |
|------|---------------------|-------------------|
| **서명 없음** | 0.1ms | 10,000 |
| **RSA 서명** | 1.0ms | 1,000 |
| **HMAC 서명** | 0.2ms | 5,000 |

---

## 대안: HMAC (대칭키)

RSA보다 빠른 HMAC 사용 가능:

```java
public class HmacMessageSigner {
    
    private final SecretKey secretKey;
    
    public String sign(String message) {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);
        byte[] signatureBytes = mac.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(signatureBytes);
    }
    
    public boolean verify(String message, String signature) {
        String expectedSignature = sign(message);
        return MessageDigest.isEqual(
            expectedSignature.getBytes(),
            signature.getBytes()
        );
    }
}
```

**장점**: RSA보다 5배 빠름  
**단점**: 대칭키 (Producer/Consumer 모두 같은 키 필요)

---

## 결론

**메시지 서명은 `TRUSTED_PACKAGES: "*"`와 함께 사용하여 유연성과 보안을 모두 확보하는 최선의 방법입니다.**

### 적용 시나리오
- ✅ 외부 시스템 연동
- ✅ 멀티 테넌트 환경
- ✅ 금융/보안 중요 시스템
- ✅ 규제 준수 필요 (감사 추적)

### 다음 단계
- [API Gateway 패턴](./API_GATEWAY_PATTERN.md) - 더 강력한 보안
- [String vs Object 선택](./PAYLOAD_SELECTION_GUIDE.md) - 페이로드 설계
