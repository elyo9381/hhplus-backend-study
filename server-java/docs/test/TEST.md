# 테스트 전략

## 테스트 분류

### 단위 테스트 (Unit Test)
```bash
./gradlew unitTest
```
- Mock 기반 테스트
- DB/컨테이너 불필요
- 실행 시간: ~5초

### 통합 테스트 (Integration Test)
```bash
./gradlew integrationTest
```
- Testcontainers MySQL 사용
- 실제 DB 연동 테스트
- 실행 시간: ~45초

## 테스트 구조

```
src/test/java/
├── TestContainerSupport.java    # Testcontainers 설정 (lazy init)
├── AbstractIntegrationTest.java # 통합 테스트 베이스 클래스
├── integration/                 # 통합 테스트
├── outbox/                      # Outbox 패턴 테스트
└── {domain}/
    ├── *Test.java               # 단위 테스트
    ├── *RepositoryTest.java     # Repository 테스트
    └── *IntegrationTest.java    # 통합 테스트
```

## 테스트 분류 기준

| 패턴 | 분류 | 설명 |
|------|------|------|
| `*Test.java` | 단위 | Mock 기반 |
| `*RepositoryTest.java` | 통합 | DB 필요 |
| `*IntegrationTest.java` | 통합 | 전체 플로우 |
| `*ConcurrencyTest.java` | 통합 | 동시성 테스트 |
| `*LockTest.java` | 통합 | 락 테스트 |
| `outbox/*` | 통합 | Outbox 패턴 |

## 주요 설정

### TestContainerSupport
- Singleton Container 패턴
- Lazy initialization (단위 테스트 시 컨테이너 미시작)

### AbstractIntegrationTest
- `@SpringBootTest` + `@ActiveProfiles("test")`
- `@Transactional` 미적용 (개별 테스트에서 필요시 적용)
- 동시성/스케줄러 테스트 호환

### MockMessageProducer
- `@Profile("test")` + `@Primary`
- 테스트 환경에서 외부 API 호출 대체

## 주의사항

### 동시성 테스트
- `@Transactional` 사용 금지
- 각 스레드가 별도 트랜잭션 필요
- `@BeforeEach`에서 데이터 정리 필수

### 스케줄러 테스트
- `@Transactional` 사용 금지
- 스케줄러는 별도 트랜잭션에서 실행
- 테스트 데이터가 커밋되어야 스케줄러에서 조회 가능

### 데이터 정리
```java
@BeforeEach
void setUp() {
    outboxRepository.deleteAll();
    mockMessageProducer.clear();
}
```
