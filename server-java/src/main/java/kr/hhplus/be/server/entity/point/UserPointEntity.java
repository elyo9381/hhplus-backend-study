package kr.hhplus.be.server.entity.point;

import jakarta.persistence.*;
import kr.hhplus.be.server.entity.user.UserEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPointEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiredAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
    }

    public UserPointEntity(UserEntity user) {
        this.user = user;
        this.amount = BigDecimal.ZERO;
        this.expiredAt = LocalDateTime.now().plusYears(1);
    }

    public UserPointEntity(UUID userId) {
        this.user = null;
        this.amount = BigDecimal.ZERO;
        this.expiredAt = LocalDateTime.now().plusYears(1);
    }

    public void charge(BigDecimal amount) {
        this.amount = this.amount.add(amount);
    }

    public void use(BigDecimal amount) {
        if (this.amount.compareTo(amount) < 0) {
            throw new IllegalStateException("포인트가 부족합니다");
        }
        this.amount = this.amount.subtract(amount);
    }
}
