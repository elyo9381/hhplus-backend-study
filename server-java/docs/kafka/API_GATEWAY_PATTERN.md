# API Gateway 패턴

## 개요

API Gateway는 **외부 입력을 신뢰 경계(Trust Boundary)에서 차단하고, 내부 시스템으로 안전한 메시지만 전달**하는 패턴입니다.

### 아키텍처

```
외부 시스템 (신뢰 안 됨)
    ↓
    ↓ HTTP/REST
    ↓
┌─────────────────────────────────────┐
│      API Gateway (Trust Boundary)    │
│  1. 인증/인가                         │
│  2. 입력 검증                         │
│  3. Rate Limiting                    │
│  4. DTO 변환 (화이트리스트)           │
│  5. 메시지 서명                       │
└─────────────────────────────────────┘
    ↓
    ↓ Kafka (서명된 메시지)
    ↓
내부 시스템 (신뢰됨)
    ↓
Consumer (TRUSTED_PACKAGES: "*")
```

---

## 핵심 원칙

### 1. Zero Trust
```
외부 입력 → 절대 신뢰하지 않음
API Gateway → 신뢰 경계 (모든 검증 수행)
내부 Kafka → 서명된 메시지만 신뢰
```

### 2. Defense in Depth (다층 방어)
```
Layer 1: API Gateway 인증/인가
Layer 2: 입력 검증 (Bean Validation)
Layer 3: DTO 변환 (화이트리스트)
Layer 4: Rate Limiting
Layer 5: 메시지 서명
Layer 6: Consumer 서명 검증
```

### 3. Fail-Safe
```
검증 실패 → 즉시 차단 (Kafka 전송 안 함)
서명 실패 → 즉시 차단 (역직렬화 안 함)
```

---

## 구현

### 1. 외부 DTO (External DTO)

```java
package kr.hhplus.be.server.presentation.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 외부 시스템에서 받는 DTO
 * - 검증 애노테이션 포함
 * - 내부 도메인과 분리
 */
public record ExternalCouponIssueRequest(
    @NotNull(message = "쿠폰 ID는 필수입니다")
    String couponId,
    
    @NotNull(message = "사용자 ID는 필수입니다")
    String userId
) {
}
```

### 2. API Gateway Controller

```java
package kr.hhplus.be.server.presentation.api;

import kr.hhplus.be.server.application.coupon.CouponIssueRequest;
import kr.hhplus.be.server.infrastructure.kafka.SecureKafkaProducer;
import kr.hhplus.be.server.presentation.api.dto.ExternalCouponIssueRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponApiController {
    
    private final SecureKafkaProducer secureProducer;
    private final RateLimiter rateLimiter;
    
    public CouponApiController(
        SecureKafkaProducer secureProducer,
        RateLimiter rateLimiter
    ) {
        this.secureProducer = secureProducer;
        this.rateLimiter = rateLimiter;
    }
    
    /**
     * 쿠폰 발급 API (외부 노출)
     * 
     * 보안 레이어:
     * 1. Spring Security 인증/인가
     * 2. Bean Validation 입력 검증
     * 3. Rate Limiting
     * 4. DTO 변환 (화이트리스트)
     * 5. 메시지 서명
     */
    @PostMapping("/issue")
    public ResponseEntity<CouponIssueResponse> issueCoupon(
        @RequestBody @Validated ExternalCouponIssueRequest externalRequest,
        @AuthenticationPrincipal User user
    ) {
        // Layer 1: 인증/인가 (Spring Security가 자동 처리)
        // - JWT 토큰 검증
        // - 사용자 권한 확인
        
        // Layer 2: 권한 검증
        if (!user.hasPermission("ISSUE_COUPON")) {
            throw new ForbiddenException("쿠폰 발급 권한이 없습니다");
        }
        
        // Layer 3: Rate Limiting
        if (!rateLimiter.tryAcquire(user.getId(), "coupon-issue", 10, Duration.ofMinutes(1))) {
            throw new TooManyRequestsException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
        
        // Layer 4: DTO 변환 (화이트리스트)
        // 외부 DTO → 내부 DTO (명시적 변환)
        UUID requestId = UUID.randomUUID();
        CouponIssueRequest internalRequest = new CouponIssueRequest(
            requestId,
            UUID.fromString(externalRequest.couponId()),
            user.getId()  // 인증된 사용자 ID 사용 (요청의 userId 무시)
        );
        
        // Layer 5: 메시지 서명 + Kafka 전송
        secureProducer.sendSigned("coupon-issue-request", internalRequest);
        
        return ResponseEntity.accepted()
            .body(new CouponIssueResponse(requestId, "쿠폰 발급 요청이 접수되었습니다"));
    }
}
```

### 3. Rate Limiter

```java
package kr.hhplus.be.server.infrastructure.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class RateLimiter {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public RateLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Rate Limiting 체크
     * 
     * @param userId 사용자 ID
     * @param action 액션 (예: "coupon-issue")
     * @param maxRequests 최대 요청 수
     * @param duration 기간
     * @return 허용 여부
     */
    public boolean tryAcquire(UUID userId, String action, int maxRequests, Duration duration) {
        String key = String.format("rate-limit:%s:%s", action, userId);
        
        // Redis INCR + EXPIRE
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count == 1) {
            // 첫 요청 → TTL 설정
            redisTemplate.expire(key, duration);
        }
        
        return count <= maxRequests;
    }
}
```

### 4. Security Configuration

```java
package kr.hhplus.be.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/coupons/issue").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        
        return http.build();
    }
}
```

### 5. Consumer (변경 없음)

```java
@Component
public class CouponIssueConsumer {
    
    private final CouponService couponService;
    private final MessageSigner signer;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(topics = "coupon-issue-request", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
        @Payload String json,
        @Header(value = "signature", required = true) String signature
    ) {
        // Layer 6: 서명 검증
        if (!signer.verify(json, signature)) {
            throw new SecurityException("Invalid signature");
        }
        
        // 검증 성공 → 역직렬화 (TRUSTED_PACKAGES: "*" 사용)
        CouponIssueRequest request = objectMapper.readValue(json, CouponIssueRequest.class);
        
        // 비즈니스 로직 처리
        couponService.issueCouponInternal(
            request.requestId(),
            request.couponId(),
            request.userId()
        );
    }
}
```

---

## 보안 레이어 상세

### Layer 1: 인증/인가

#### JWT 토큰 검증
```java
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // 1. 토큰 만료 확인
        if (jwt.getExpiresAt().isBefore(Instant.now())) {
            throw new JwtException("Token expired");
        }
        
        // 2. 사용자 정보 추출
        String userId = jwt.getClaimAsString("sub");
        List<String> roles = jwt.getClaimAsStringList("roles");
        
        // 3. 권한 생성
        Collection<GrantedAuthority> authorities = roles.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
        
        return new JwtAuthenticationToken(jwt, authorities, userId);
    }
}
```

#### 권한 검증
```java
@Service
public class PermissionService {
    
    public boolean hasPermission(User user, String permission) {
        // 1. 사용자 역할 확인
        if (user.getRoles().contains("ADMIN")) {
            return true;  // 관리자는 모든 권한
        }
        
        // 2. 권한 매핑 확인
        return user.getPermissions().contains(permission);
    }
}
```

### Layer 2: 입력 검증

#### Bean Validation
```java
public record ExternalCouponIssueRequest(
    @NotNull(message = "쿠폰 ID는 필수입니다")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", 
             message = "올바른 UUID 형식이 아닙니다")
    String couponId,
    
    @NotNull(message = "사용자 ID는 필수입니다")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", 
             message = "올바른 UUID 형식이 아닙니다")
    String userId
) {
}
```

#### Custom Validator
```java
@Component
public class CouponIssueRequestValidator {
    
    public void validate(ExternalCouponIssueRequest request, User user) {
        // 1. 쿠폰 존재 확인
        if (!couponRepository.existsById(UUID.fromString(request.couponId()))) {
            throw new NotFoundException("쿠폰을 찾을 수 없습니다");
        }
        
        // 2. 중복 발급 확인
        if (userCouponRepository.existsByCouponIdAndUserId(
            UUID.fromString(request.couponId()), 
            user.getId()
        )) {
            throw new DuplicateException("이미 발급받은 쿠폰입니다");
        }
        
        // 3. 발급 가능 기간 확인
        Coupon coupon = couponRepository.findById(UUID.fromString(request.couponId())).orElseThrow();
        if (!coupon.isIssuable()) {
            throw new BusinessException("발급 가능한 기간이 아닙니다");
        }
    }
}
```

### Layer 3: Rate Limiting

#### 사용자별 제한
```java
// 사용자당 분당 10회
rateLimiter.tryAcquire(user.getId(), "coupon-issue", 10, Duration.ofMinutes(1));
```

#### IP별 제한
```java
// IP당 분당 100회
String clientIp = request.getRemoteAddr();
rateLimiter.tryAcquire(clientIp, "coupon-issue", 100, Duration.ofMinutes(1));
```

#### 글로벌 제한
```java
// 전체 시스템 초당 1000회
rateLimiter.tryAcquire("global", "coupon-issue", 1000, Duration.ofSeconds(1));
```

### Layer 4: DTO 변환 (화이트리스트)

#### 명시적 변환
```java
// ✅ 올바른 방법: 명시적 변환
CouponIssueRequest internalRequest = new CouponIssueRequest(
    requestId,
    UUID.fromString(externalRequest.couponId()),
    user.getId()  // 인증된 사용자 ID 사용
);

// ❌ 잘못된 방법: 자동 변환
CouponIssueRequest internalRequest = objectMapper.convertValue(externalRequest, CouponIssueRequest.class);
```

#### 필드 화이트리스트
```java
// 외부 요청에 악의적인 필드가 있어도 무시됨
{
  "couponId": "...",
  "userId": "...",
  "isAdmin": true,           // 무시됨
  "discountAmount": 999999,  // 무시됨
  "@class": "java.lang.ProcessBuilder"  // 무시됨
}

// 내부 DTO는 명시적으로 지정한 필드만 포함
CouponIssueRequest(requestId, couponId, userId)
```

### Layer 5: 메시지 서명

```java
// API Gateway에서 서명
String json = objectMapper.writeValueAsString(internalRequest);
String signature = signer.sign(json);

ProducerRecord<String, String> record = new ProducerRecord<>("coupon-issue-request", json);
record.headers().add("signature", signature.getBytes());

kafkaTemplate.send(record);
```

### Layer 6: Consumer 서명 검증

```java
// Consumer에서 검증
if (!signer.verify(json, signature)) {
    throw new SecurityException("Invalid signature");
}
```

---

## 에러 처리

### 1. Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());
        
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", errors));
    }
    
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("FORBIDDEN", e.getMessage()));
    }
    
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequestsException(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ErrorResponse("TOO_MANY_REQUESTS", e.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        // 내부 에러는 상세 정보 노출 안 함
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }
}
```

### 2. 보안 사고 로깅

```java
@Component
public class SecurityAuditLogger {
    
    private final Logger logger = LoggerFactory.getLogger(SecurityAuditLogger.class);
    
    public void logAuthenticationFailure(String userId, String reason, String ip) {
        logger.warn("Authentication failed - userId: {}, reason: {}, ip: {}", userId, reason, ip);
    }
    
    public void logRateLimitExceeded(String userId, String action, String ip) {
        logger.warn("Rate limit exceeded - userId: {}, action: {}, ip: {}", userId, action, ip);
    }
    
    public void logSignatureVerificationFailure(String topic, String message, String signature) {
        logger.error("Signature verification failed - topic: {}, message: {}, signature: {}", 
            topic, message.substring(0, 100), signature);
        
        // 심각한 보안 사고 → 알림 발송
        alertService.sendSecurityAlert("Signature verification failed", topic, message);
    }
}
```

---

## 모니터링

### 1. 메트릭

```java
@Component
public class ApiGatewayMetrics {
    
    private final Counter authenticationFailureCounter;
    private final Counter rateLimitExceededCounter;
    private final Counter validationErrorCounter;
    private final Timer requestTimer;
    
    public ApiGatewayMetrics(MeterRegistry registry) {
        this.authenticationFailureCounter = Counter.builder("api.gateway.authentication.failure")
            .description("Number of authentication failures")
            .register(registry);
        
        this.rateLimitExceededCounter = Counter.builder("api.gateway.ratelimit.exceeded")
            .description("Number of rate limit exceeded")
            .register(registry);
        
        this.validationErrorCounter = Counter.builder("api.gateway.validation.error")
            .description("Number of validation errors")
            .register(registry);
        
        this.requestTimer = Timer.builder("api.gateway.request.duration")
            .description("Request processing time")
            .register(registry);
    }
}
```

### 2. 대시보드

```
Grafana Dashboard:

┌─────────────────────────────────────┐
│  API Gateway Security Metrics       │
├─────────────────────────────────────┤
│  Authentication Failures: 12 /hour  │
│  Rate Limit Exceeded: 45 /hour      │
│  Validation Errors: 23 /hour        │
│  Signature Failures: 0 /hour        │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Request Latency (p99)              │
├─────────────────────────────────────┤
│  Authentication: 50ms               │
│  Validation: 10ms                   │
│  Rate Limiting: 5ms                 │
│  Kafka Send: 20ms                   │
│  Total: 85ms                        │
└─────────────────────────────────────┘
```

---

## 테스트

### 1. 보안 테스트

```java
@SpringBootTest
@AutoConfigureMockMvc
class CouponApiSecurityTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void 인증_없이_요청_시_401() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponId\":\"...\",\"userId\":\"...\"}"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    void 권한_없이_요청_시_403() throws Exception {
        String token = generateTokenWithoutPermission();
        
        mockMvc.perform(post("/api/v1/coupons/issue")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponId\":\"...\",\"userId\":\"...\"}"))
            .andExpect(status().isForbidden());
    }
    
    @Test
    void Rate_Limit_초과_시_429() throws Exception {
        String token = generateValidToken();
        
        // 11번 요청 (제한: 10회/분)
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(post("/api/v1/coupons/issue")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"couponId\":\"...\",\"userId\":\"...\"}"))
                .andExpect(i < 10 ? status().isAccepted() : status().isTooManyRequests());
        }
    }
    
    @Test
    void 잘못된_입력_시_400() throws Exception {
        String token = generateValidToken();
        
        mockMvc.perform(post("/api/v1/coupons/issue")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponId\":\"invalid\",\"userId\":\"...\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
```

### 2. 통합 테스트

```java
@SpringBootTest
@Testcontainers
class ApiGatewayIntegrationTest extends TestContainerSupport {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private CouponIssueStatusRepository statusRepository;
    
    @Test
    void API_Gateway를_통한_전체_플로우_성공() throws Exception {
        // given
        String token = generateValidToken();
        UUID couponId = createTestCoupon();
        
        // when
        MvcResult result = mockMvc.perform(post("/api/v1/coupons/issue")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"couponId\":\"%s\",\"userId\":\"%s\"}", couponId, userId)))
            .andExpect(status().isAccepted())
            .andReturn();
        
        String requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");
        
        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            CouponIssueStatus status = statusRepository.findById(UUID.fromString(requestId)).orElseThrow();
            assertThat(status.getStatus()).isEqualTo(SUCCESS);
        });
    }
}
```

---

## 장단점 분석

### 장점
1. ✅ **최고 수준의 보안**: 다층 방어
2. ✅ **명확한 책임 분리**: API Gateway = 보안, Consumer = 비즈니스 로직
3. ✅ **외부 공격 차단**: 신뢰 경계에서 모든 검증
4. ✅ **감사 추적**: 모든 요청 로깅
5. ✅ **유연성**: 내부 시스템은 `TRUSTED_PACKAGES: "*"` 사용 가능

### 단점
1. ❌ **복잡도 증가**: 인증/인가, Rate Limiting, 서명 등
2. ❌ **레이턴시 증가**: 여러 검증 레이어 (~50-100ms)
3. ❌ **운영 부담**: 키 관리, 모니터링, 알림 설정

---

## 결론

**API Gateway 패턴은 외부 시스템과 연동 시 필수적인 보안 아키텍처입니다.**

### 적용 시나리오
- ✅ 외부 API 노출
- ✅ 멀티 테넌트 환경
- ✅ 공개 서비스
- ✅ 규제 준수 필요 (금융, 의료 등)

### 다음 단계
- [String vs Object 선택](./PAYLOAD_SELECTION_GUIDE.md) - 페이로드 설계
- [JSON 역직렬화 보안](./JSON_DESERIALIZATION_SECURITY.md) - 보안 기초
