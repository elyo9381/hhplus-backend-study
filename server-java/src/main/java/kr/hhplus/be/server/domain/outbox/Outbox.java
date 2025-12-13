package kr.hhplus.be.server.domain.outbox;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox 패턴을 위한 도메인 모델
 * 
 * 목적:
 * - 트랜잭션 정합성 보장 (비즈니스 로직과 같은 트랜잭션에서 저장)
 * - At-Least-Once 전송 보장
 * - 외부 시스템 메시지 발행 (Kafka, HTTP API 등)
 */
@Getter
public class Outbox {
    private final UUID id;
    private final String eventType;        // ORDER_CREATED, PAYMENT_COMPLETED
    private final UUID aggregateId;        // Order ID, Payment ID
    private final String payload;          // JSON 직렬화된 이벤트 데이터
    private OutboxStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private Integer retryCount;
    private String errorMessage;

    // 이벤트 생성 시 사용하는 생성자
    public Outbox(String eventType, UUID aggregateId, String payload) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.retryCount = 0;
    }

    // Entity에서 도메인으로 변환 시 사용하는 생성자
    public Outbox(UUID id, String eventType, UUID aggregateId, String payload,
                  OutboxStatus status, LocalDateTime createdAt, LocalDateTime publishedAt,
                  Integer retryCount, String errorMessage) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.retryCount = retryCount;
        this.errorMessage = errorMessage;
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public void markAsFailed() {
        this.status = OutboxStatus.FAILED;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
