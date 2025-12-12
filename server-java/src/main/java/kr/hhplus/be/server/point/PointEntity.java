package kr.hhplus.be.server.point;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    public PointEntity(UUID userId, Long amount, LocalDateTime expiredAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }

    public void use(Long useAmount) {
        if (this.amount < useAmount) {
            throw new IllegalArgumentException("Insufficient point");
        }
        this.amount -= useAmount;
    }
}
