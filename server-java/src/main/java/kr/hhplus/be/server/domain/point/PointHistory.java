package kr.hhplus.be.server.domain.point;

import kr.hhplus.be.server.domain.point.PointHistoryType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PointHistory {
    private UUID id;
    private UUID userId;
    private PointHistoryType type;
    private BigDecimal amount;
    private BigDecimal balance;
    private String description;
    private LocalDateTime createdAt;

    public static PointHistory create(UUID userId, PointHistoryType type, BigDecimal amount, BigDecimal balance, String description) {
        return new PointHistory(
                UUID.randomUUID(),
                userId,
                type,
                amount,
                balance,
                description,
                LocalDateTime.now()
        );
    }
}
