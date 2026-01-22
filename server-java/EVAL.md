# Evaluation Criteria

## 복잡도 판단 기준

| 복잡도 | 기준 | 검토 방식 |
|--------|------|----------|
| **LOW** | 설정 변경, 오타 수정, 주석 추가, DTO 필드 추가 | 바로 완료 |
| **MEDIUM** | 새 메서드 추가, 버그 수정, 단순 리팩토링 | 기본 검토 |
| **HIGH** | 새 도메인/API, DB 스키마 변경, 캐시 전략 변경 | 전문 검토 + 테스트 |
| **CRITICAL** | 동시성 로직, 트랜잭션 경계, 락 전략, 정합성 로직 | 심층 검토 + 동시성 테스트 필수 |

---

## 기능 검증 (모든 복잡도)

- [ ] 요구사항 충족: 태스크 설명과 출력물 일치
- [ ] 엣지 케이스: null, 빈 값, 경계값 처리
- [ ] 에러 핸들링: 적절한 예외 타입 (`IllegalStateException`, `IllegalArgumentException`)

## 코드 품질 (MEDIUM 이상)

- [ ] 빌드 통과: `./gradlew build`
- [ ] 테스트 통과: `./gradlew test`
- [ ] 메서드 길이: 30줄 이하
- [ ] 중복 코드: DRY 원칙 준수
- [ ] 네이밍: camelCase, 의미 있는 이름
- [ ] Lombok: `@Getter`, `@RequiredArgsConstructor` 활용

### Comment Checker (AI 코드 품질)

AI 생성 코드가 사람 코드와 구분 불가해야 함:
- [ ] 불필요한 주석 없음 (코드로 설명 가능한 내용은 주석 금지)
- [ ] TODO/FIXME 외 임시 주석 없음
- [ ] 과도한 설명 주석 금지 ("// 사용자 ID를 가져온다" 같은 자명한 주석)
- [ ] 주석이 있다면 "왜(Why)"를 설명, "무엇(What)"은 코드로

## DDD 레이어 준수 (HIGH 이상)

### 의존성 방향
```
presentation → application → domain
                    ↓
              infrastructure
```

- [ ] domain: 외부 의존성 없음 (순수 Java)
- [ ] application: domain만 의존, infrastructure 직접 참조 금지
- [ ] infrastructure: domain 인터페이스 구현
- [ ] presentation: application만 호출

### 도메인 모델
- [ ] 비즈니스 로직은 도메인 객체 내부에 (`order.completePayment()`)
- [ ] setter 사용 금지, 의미 있는 메서드명 사용
- [ ] 생성자 2개 패턴: 신규 생성용 + Entity 변환용

### Repository
- [ ] 인터페이스는 domain 패키지에
- [ ] 구현체는 infrastructure 패키지에
- [ ] JpaRepository는 infrastructure 내부에서만 사용

## 동시성 검증 (CRITICAL)

### 락 전략
- [ ] 재고/포인트: 비관적 락 (`@Lock(PESSIMISTIC_WRITE)`)
- [ ] 선착순 쿠폰: Redisson 분산락
- [ ] 락 획득 순서: ID 정렬로 데드락 방지

### 트랜잭션
- [ ] `@Transactional`은 Service 메서드에만
- [ ] 읽기 전용: `@Transactional(readOnly = true)`
- [ ] self-invocation 없음 (같은 클래스 내 호출 금지)

### Redis 정합성
- [ ] `TransactionSynchronization`으로 롤백 처리
- [ ] Redis 실패 시 예외 발생 (DB 저장 방지)

### 동시성 테스트
- [ ] `ExecutorService` + `CountDownLatch` 패턴
- [ ] `AtomicInteger`로 성공/실패 카운트
- [ ] 예상 결과와 실제 결과 검증

## 보안 (HIGH 이상)

- [ ] SQL Injection: JPA 파라미터 바인딩 사용
- [ ] 입력 검증: `@Valid` + Bean Validation
- [ ] 민감정보: 하드코딩 없음, `application.yml` 또는 환경변수
- [ ] 사용자 검증: `userId` 일치 확인 (결제 시)

## 성능 (HIGH 이상)

- [ ] N+1 쿼리: `@EntityGraph` 또는 fetch join
- [ ] 인덱스: 조회 조건 컬럼에 `@Index`
- [ ] 캐싱: `@Cacheable` 또는 Redis 캐시
- [ ] 비핵심 기능 분리: 랭킹 업데이트 실패해도 결제 성공

## 테스트 (CRITICAL)

### 테스트 종류
- [ ] 도메인 단위 테스트: `{Domain}Test.java`
- [ ] 서비스 Mock 테스트: `{Domain}ServiceTest.java`
- [ ] Repository 통합 테스트: `{Domain}RepositoryTest.java`
- [ ] 동시성 테스트: `{Domain}ConcurrencyTest.java`

### 테스트 환경
- [ ] Testcontainers: MySQL, Redis
- [ ] `@DynamicPropertySource`로 컨테이너 설정 주입
- [ ] `@ActiveProfiles("test")` 사용

---

## 검증 명령어

```bash
# 전체 빌드 + 테스트
./gradlew build

# 테스트만
./gradlew test

# 특정 도메인 테스트
./gradlew test --tests "kr.hhplus.be.server.coupon.*"

# 동시성 테스트
./gradlew test --tests "*ConcurrencyTest"

# 통합 테스트
./gradlew test --tests "*IntegrationTest"

# 단일 테스트
./gradlew test --tests "CouponServiceTest.쿠폰_발급_성공"
```

---

## 체크리스트 by 복잡도

### LOW
- [ ] 빌드 통과

### MEDIUM
- [ ] 빌드 통과
- [ ] 관련 단위 테스트 통과
- [ ] 코드 품질 체크

### HIGH
- [ ] 빌드 통과
- [ ] 단위 + 통합 테스트 통과
- [ ] DDD 레이어 준수
- [ ] 보안/성능 체크

### CRITICAL
- [ ] 빌드 통과
- [ ] 모든 테스트 통과
- [ ] 동시성 테스트 필수
- [ ] 락 전략 검증
- [ ] 트랜잭션 경계 검증
- [ ] Redis 정합성 검증

---

## 피드백 루프 규칙

- 최대 반복: **3회**
- 3회 실패 시:
  1. 현재까지 결과 저장
  2. 문제점 요약 작성
  3. ESCALATION.md 생성
