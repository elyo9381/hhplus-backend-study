package kr.hhplus.be.server.infrastructure.point.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PointRepository extends JpaRepository<PointEntity, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PointEntity> findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(UUID userId, LocalDateTime now);
    
    @Query("SELECT p FROM PointEntity p WHERE p.userId = :userId AND p.expiredAt > :now ORDER BY p.expiredAt")
    List<PointEntity> findByUserIdAndExpiredAtAfterReadOnly(UUID userId, LocalDateTime now);
}
