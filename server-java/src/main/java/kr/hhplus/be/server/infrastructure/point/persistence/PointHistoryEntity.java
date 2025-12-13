package kr.hhplus.be.server.infrastructure.point.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "point_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointHistoryType type;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long balance;

    @Column
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public PointHistoryEntity(UUID userId, PointHistoryType type, Long amount, Long balance, String description) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balance = balance;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }
}
