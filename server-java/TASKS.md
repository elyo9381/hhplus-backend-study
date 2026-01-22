# TASKS

## 메타데이터
- 복잡도: HIGH
- 검토 방식: 전문 Coach 팀 병렬 검토
- 생성일: 2026-01-22
- 상태: 완료

## 요구사항
실시간 주문정보(이커머스) 데이터 플랫폼에 전송(mock API 호출)하는 요구사항을 이벤트를 활용하여 트랜잭션과 관심사를 분리하여 서비스를 개선

## 태스크 목록
| # | 태스크 | 상태 | 담당 | 반복횟수 |
|---|--------|------|------|---------|
| 1 | PaymentCompletedEvent 도메인 이벤트 생성 | [x] | player | 0 |
| 2 | DataPlatformClient (Mock API 호출) 생성 | [x] | player | 0 |
| 3 | DataPlatformEventListener (@TransactionalEventListener) 생성 | [x] | player | 0 |
| 4 | PaymentService에서 이벤트 발행 추가 | [x] | player | 0 |
| 5 | 빌드 및 테스트 검증 | [x] | player | 0 |
| 6 | Coach 팀 피드백 반영 | [x] | player | 0 |

## Coach 팀 검토 결과
| Coach | 결과 | 주요 피드백 |
|-------|------|------------|
| security-coach | PASS (수정 후) | 로그에서 민감정보 제거 |
| perf-coach | PASS (수정 후) | @EnableAsync + 스레드풀 설정 추가 |
| style-coach | CONDITIONAL PASS | EventPort 인터페이스 권장 (선택사항) |

## 변경된 파일
- `src/main/java/kr/hhplus/be/server/domain/payment/PaymentCompletedEvent.java` (신규)
- `src/main/java/kr/hhplus/be/server/infrastructure/platform/DataPlatformClient.java` (신규)
- `src/main/java/kr/hhplus/be/server/infrastructure/platform/DataPlatformEventListener.java` (신규)
- `src/main/java/kr/hhplus/be/server/config/AsyncConfig.java` (신규)
- `src/main/java/kr/hhplus/be/server/application/payment/PaymentService.java` (수정)
- `src/main/java/kr/hhplus/be/server/ServerApplication.java` (수정 - @EnableAsync)
- `src/test/java/kr/hhplus/be/server/payment/domain/PaymentServiceTest.java` (수정)
- `src/test/java/kr/hhplus/be/server/coupon/CouponServiceTest.java` (수정 - 기존 버그 수정)
