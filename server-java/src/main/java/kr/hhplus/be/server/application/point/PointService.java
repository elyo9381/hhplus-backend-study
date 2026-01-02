package kr.hhplus.be.server.application.point;

import kr.hhplus.be.server.application.payment.PointPort;
import kr.hhplus.be.server.infrastructure.point.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PointService implements PointPort {

    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;

    public PointService(PointRepository pointRepository, PointHistoryRepository pointHistoryRepository) {
        this.pointRepository = pointRepository;
        this.pointHistoryRepository = pointHistoryRepository;
    }

    @Transactional
    public PointEntity chargePoint(UUID userId, Long amount) {
        // 1. 현재 잔액 계산
        Long currentBalance = getAvailablePoints(userId);
        
        // 2. 새 포인트 생성 (만료일 1년 후)
        LocalDateTime expiredAt = LocalDateTime.now().plusYears(1);
        PointEntity point = new PointEntity(userId, amount, expiredAt);
        PointEntity saved = pointRepository.save(point);
        
        // 3. 이력 저장
        Long newBalance = currentBalance + amount;
        PointHistoryEntity history = new PointHistoryEntity(
                userId, PointHistoryType.CHARGE, amount, newBalance, "포인트 충전"
        );
        pointHistoryRepository.save(history);
        
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Long getAvailablePoints(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        return pointRepository.findByUserIdAndExpiredAtAfterReadOnly(userId, now)
                .stream()
                .mapToLong(PointEntity::getAmount)
                .sum();
    }

    @Override
    @Transactional
    public void usePoint(UUID userId, Long amount) {
        // 1. 만료되지 않은 포인트 조회 (락)
        LocalDateTime now = LocalDateTime.now();
        var points = pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(userId, now);
        
        // 2. 잔액 확인
        Long totalBalance = points.stream()
                .mapToLong(PointEntity::getAmount)
                .sum();
        
        if (totalBalance < amount) {
            throw new IllegalArgumentException("Insufficient point balance");
        }
        
        // 3. 만료일 순으로 차감
        Long remaining = amount;
        for (PointEntity point : points) {
            if (remaining <= 0) break;
            
            Long useAmount = Math.min(point.getAmount(), remaining);
            point.use(useAmount);
            remaining -= useAmount;
        }
        
        // 4. 이력 저장
        Long newBalance = totalBalance - amount;
        PointHistoryEntity history = new PointHistoryEntity(
                userId, PointHistoryType.USE, amount, newBalance, "포인트 사용"
        );
        pointHistoryRepository.save(history);
    }
}
