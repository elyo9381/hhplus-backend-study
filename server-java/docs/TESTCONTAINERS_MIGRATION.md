# Testcontainers 마이그레이션 가이드

## 변경 사항 요약

H2 인메모리 DB → Testcontainers MySQL 8.0.40

## 1. 의존성 변경

### build.gradle.kts
```kotlin
// 변경 전
testRuntimeOnly("com.h2database:h2")

// 변경 후
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:mysql")
```

## 2. Base Test 클래스

### AbstractIntegrationTest.java
모든 통합 테스트는 이 클래스를 상속받아야 합니다.

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {
    
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
}
```

**특징:**
- `static` 컨테이너: 모든 테스트가 하나의 MySQL 컨테이너 공유 (성능 최적화)
- `@ServiceConnection`: Spring Boot가 자동으로 DataSource 설정
- `@Transactional`: 각 테스트 후 자동 롤백 (데이터 격리)

## 3. 테스트 클래스 수정 방법

### 변경 전
```java
@SpringBootTest
class OrderServiceIntegrationTest {
    // ...
}
```

### 변경 후
```java
class OrderServiceIntegrationTest extends AbstractIntegrationTest {
    // ...
}
```

**제거할 애노테이션:**
- `@SpringBootTest` (AbstractIntegrationTest에 있음)
- `@AutoConfigureTestDatabase` (불필요)
- `@DirtiesContext` (대부분 불필요, @Transactional로 대체)

## 4. 수정 대상 테스트 목록

### 통합 테스트 (AbstractIntegrationTest 상속 필요)
```
✅ ServerApplicationTests
✅ SimpleSpringBootTest
□ OrderServiceIntegrationTest
□ OrderRepositoryTest
□ OrderConcurrencyTest
□ PaymentServiceIntegrationTest
□ PaymentRepositoryTest
□ ConcurrencyIntegrationTest
□ PointRepositoryTest
□ PointServiceTest
□ ProductRepositoryTest
□ UserRepositoryTest
```

### 단위 테스트 (수정 불필요)
```
- OrderTest
- OrderItemTest
- PaymentTest
- 기타 도메인 모델 테스트
```

## 5. 일괄 수정 스크립트

### 방법 1: 수동 수정
각 통합 테스트 파일을 열어서:
1. `@SpringBootTest` 제거
2. `extends AbstractIntegrationTest` 추가
3. `@AutoConfigureTestDatabase` 제거 (있다면)

### 방법 2: sed 명령어 (macOS/Linux)
```bash
# 백업 생성
cp -r src/test/java src/test/java.backup

# @SpringBootTest를 extends AbstractIntegrationTest로 변경
find src/test/java -name "*IntegrationTest.java" -o -name "*RepositoryTest.java" | while read file; do
    # @SpringBootTest 제거하고 extends 추가
    sed -i '' 's/@SpringBootTest//' "$file"
    sed -i '' 's/class \(.*\) {/class \1 extends AbstractIntegrationTest {/' "$file"
done
```

## 6. 설정 파일 변경

### application.yml (test)
```yaml
spring:
  # Testcontainers가 자동 설정하므로 datasource 불필요
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect  # H2Dialect → MySQLDialect
```

## 7. 테스트 실행

### 로컬 실행
```bash
# Docker Desktop 실행 확인
docker ps

# 전체 테스트 실행
./gradlew clean test

# 특정 테스트만 실행
./gradlew test --tests OrderServiceIntegrationTest
```

### 첫 실행 시
- MySQL 8.0.40 이미지 다운로드 (약 1-2분)
- 이후 실행은 빠름 (이미지 캐시됨)

## 8. 성능 비교

### H2 (변경 전)
```
전체 테스트 실행 시간: ~10초
컨테이너 시작 시간: 0초
```

### Testcontainers MySQL (변경 후)
```
전체 테스트 실행 시간: ~15-20초 (첫 실행)
전체 테스트 실행 시간: ~12-15초 (이후 실행)
컨테이너 시작 시간: ~3-5초 (Singleton 패턴으로 한 번만)
```

## 9. 트러블슈팅

### 문제 1: Docker가 실행되지 않음
```
Error: Could not find a valid Docker environment
```
**해결:** Docker Desktop 실행

### 문제 2: 포트 충돌
```
Error: Port 3306 is already in use
```
**해결:** 로컬 MySQL 중지 또는 Testcontainers가 랜덤 포트 사용 (자동 해결)

### 문제 3: 메모리 부족
```
Error: Container failed to start
```
**해결:** Docker Desktop 메모리 할당 증가 (최소 2GB)

### 문제 4: 테스트 간 데이터 오염
```
Expected 1 but was 2
```
**해결:** 
- `@Transactional` 추가 (AbstractIntegrationTest에 이미 있음)
- 또는 `@BeforeEach`에서 데이터 정리

## 10. CI/CD 설정

### GitHub Actions
```yaml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run tests
        run: ./gradlew test
```

**주의:** GitHub Actions는 Docker를 기본 제공하므로 추가 설정 불필요

## 11. 장점 확인

### MySQL 전용 기능 테스트 가능
```java
@Test
void testPartitioning() {
    // 파티셔닝 쿼리 실행
    jdbcTemplate.execute("""
        ALTER TABLE query_log 
        PARTITION BY RANGE (TO_DAYS(executed_at))
    """);
    
    // 실제 MySQL에서만 동작
}

@Test
void testPessimisticLock() {
    // 비관적 락 테스트
    Product product = productRepository.findByIdWithLock(productId);
    
    // 실제 MySQL 락 동작 확인
}
```

### 프로덕션과 동일한 환경
```java
@Test
void testTransactionIsolation() {
    // REPEATABLE READ 격리 수준 테스트
    // H2와 MySQL의 동작 차이 없음
}
```

## 12. 다음 단계

1. ✅ 의존성 추가
2. ✅ AbstractIntegrationTest 생성
3. ✅ application.yml 수정
4. ✅ 기본 테스트 수정
5. □ 모든 통합 테스트 수정
6. □ 전체 테스트 실행 및 검증
7. □ MySQL 전용 기능 테스트 추가
8. □ 성능 측정 및 최적화

## 13. 참고 자료

- [Testcontainers 공식 문서](https://testcontainers.com/)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.testcontainers)
- [MySQL Testcontainers](https://testcontainers.com/modules/mysql/)
