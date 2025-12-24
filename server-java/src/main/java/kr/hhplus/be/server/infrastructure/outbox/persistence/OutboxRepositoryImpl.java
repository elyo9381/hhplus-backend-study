package kr.hhplus.be.server.infrastructure.outbox.persistence;

import kr.hhplus.be.server.domain.outbox.Outbox;
import kr.hhplus.be.server.domain.outbox.OutboxRepository;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository jpaRepository;

    public OutboxRepositoryImpl(OutboxJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Outbox save(Outbox outbox) {
        OutboxEntity entity = OutboxEntity.from(outbox);
        OutboxEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<Outbox> findByStatus(OutboxStatus status) {
        return jpaRepository.findByStatus(status)
                .stream()
                .map(OutboxEntity::toDomain)
                .toList();
    }

    @Override
    public List<Outbox> findByStatusAndRetryCountLessThan(OutboxStatus status, int maxRetryCount) {
        return jpaRepository.findByStatusAndRetryCountLessThan(status, maxRetryCount)
                .stream()
                .map(OutboxEntity::toDomain)
                .toList();
    }
}
