package kr.hhplus.be.server.infrastructure.outbox.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_status_retry", columnList = "status, retry_count"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor
public class OutboxEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public static OutboxEntity from(Outbox outbox) {
        OutboxEntity entity = new OutboxEntity();
        entity.id = outbox.getId();
        entity.eventType = outbox.getEventType();
        entity.aggregateId = outbox.getAggregateId();
        entity.payload = outbox.getPayload();
        entity.status = outbox.getStatus();
        entity.createdAt = outbox.getCreatedAt();
        entity.publishedAt = outbox.getPublishedAt();
        entity.retryCount = outbox.getRetryCount();
        entity.errorMessage = outbox.getErrorMessage();
        return entity;
    }

    public Outbox toDomain() {
        return new Outbox(
                id,
                eventType,
                aggregateId,
                payload,
                status,
                createdAt,
                publishedAt,
                retryCount,
                errorMessage
        );
    }
}
