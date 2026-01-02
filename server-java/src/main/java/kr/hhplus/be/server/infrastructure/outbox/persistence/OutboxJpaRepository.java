package kr.hhplus.be.server.infrastructure.outbox.persistence;

import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {
    List<OutboxEntity> findByStatus(OutboxStatus status);
    List<OutboxEntity> findByStatusAndRetryCountLessThan(OutboxStatus status, int maxRetryCount);
}
