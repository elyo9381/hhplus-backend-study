# Docker Compose Kafka 클러스터 상세 분석

## 📋 전체 구조

```
┌─────────────────────────────────────────┐
│         Zookeeper (2181)                │
│    - 클러스터 메타데이터 관리            │
│    - Leader 선출 조율                   │
└─────────────────────────────────────────┘
              ↑  ↑  ↑
              │  │  │
    ┌─────────┼──┼──┼─────────┐
    │         │  │  │         │
┌───┴───┐ ┌──┴──┴──┴──┐ ┌────┴────┐
│Broker1│ │  Broker2  │ │ Broker3 │
│ :9092 │ │   :9093   │ │  :9094  │
└───────┘ └───────────┘ └─────────┘
```

---

## 🔍 서비스별 상세 분석

### 1. Zookeeper

```yaml
zookeeper:
  image: confluentinc/cp-zookeeper:7.6.0
  hostname: zookeeper
  container_name: zookeeper
  ports:
    - "2181:2181"
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_TICK_TIME: 2000
```

**역할:**
- Kafka 클러스터의 메타데이터 저장
- Broker 상태 관리
- Leader 선출 조율

**설정 분석:**

| 설정 | 값 | 설명 |
|------|-----|------|
| `ZOOKEEPER_CLIENT_PORT` | 2181 | Kafka가 연결할 포트 |
| `ZOOKEEPER_TICK_TIME` | 2000 | 기본 시간 단위 (2초) |

---

### 2. Broker 1, 2, 3

#### 핵심 설정 분석

**1. KAFKA_BROKER_ID**
```yaml
KAFKA_BROKER_ID: 1  # Broker 2는 2, Broker 3은 3
```
- 클러스터 내 Broker의 고유 ID

**2. KAFKA_ADVERTISED_LISTENERS** ⭐ 중요
```yaml
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://broker1:29092,PLAINTEXT_HOST://localhost:9092
```

```
PLAINTEXT://broker1:29092
└─ Docker 네트워크 내부에서 접근 (다른 컨테이너)

PLAINTEXT_HOST://localhost:9092
└─ 호스트(Mac/Windows)에서 접근
```

**포트 매핑:**
```
Broker 1: 내부 29092, 외부 9092
Broker 2: 내부 29093, 외부 9093
Broker 3: 내부 29094, 외부 9094
```

**3. Replication 설정 (개발용)**
```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
```

**4. 보관 정책**
```yaml
KAFKA_LOG_RETENTION_MS: 604800000  # 7일
KAFKA_LOG_RETENTION_BYTES: 1073741824  # 1GB
```

---

## 🔧 프로덕션 vs 개발 설정 비교

| 설정 | 현재 (개발) | 프로덕션 권장 |
|------|------------|--------------|
| `OFFSETS_TOPIC_REPLICATION_FACTOR` | 1 | 3 |
| `TRANSACTION_STATE_LOG_MIN_ISR` | 1 | 2 |
| `TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | 1 | 3 |
| `LOG_RETENTION_MS` | 7일 | 30일+ |
| `LOG_RETENTION_BYTES` | 1GB | 10GB+ |

---

## 🚀 실행 방법

```bash
# 클러스터 시작
docker-compose -f docker-compose.kafka.yaml up -d

# 상태 확인
docker ps

# Topic 목록 확인
docker exec -it broker1 kafka-topics --bootstrap-server localhost:9092 --list

# 클러스터 종료
docker-compose -f docker-compose.kafka.yaml down
```

---

## 📊 클러스터 구성 요약

```
구성:
- Zookeeper: 1대
- Kafka Broker: 3대
- Replication Factor: 1 (개발용)

접속 정보:
- Broker 1: localhost:9092
- Broker 2: localhost:9093
- Broker 3: localhost:9094

보관 정책:
- 시간: 7일
- 크기: 1GB/파티션
```

---

## ⚠️ 주의사항

1. **Replication Factor = 1**: 개발용 설정, 프로덕션에서는 RF=3 권장
2. **메모리 사용량**: Broker 3대 + Zookeeper = 약 2-3GB RAM
3. **포트 충돌**: 9092, 9093, 9094, 2181 포트 확인 필요
