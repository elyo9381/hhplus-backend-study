package kr.hhplus.be.server.domain.point;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserPoint {
    private UUID id;
    private UUID userId;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;

    public static UserPoint create(UUID userId) {
        return new UserPoint(
                UUID.randomUUID(),
                userId,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                LocalDateTime.now().plusYears(1)
        );
    }

    public void charge(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다");
        }
        this.amount = this.amount.add(amount);
    }

    public void use(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다");
        }
        if (this.amount.compareTo(amount) < 0) {
            throw new IllegalStateException("포인트가 부족합니다");
        }
        this.amount = this.amount.subtract(amount);
    }

    public void refund(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다");
        }
        this.amount = this.amount.add(amount);
    }

    public boolean hasEnough(BigDecimal amount) {
        return this.amount.compareTo(amount) >= 0;
    }
}
