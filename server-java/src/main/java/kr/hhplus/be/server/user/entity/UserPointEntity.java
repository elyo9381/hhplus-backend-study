package kr.hhplus.be.server.user.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_point")
public class UserPointEntity {

    protected UserPointEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id ;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column
    private long amount;

    @Enumerated(EnumType.STRING)
    private PointType type;

    private LocalDateTime expairedAt;

    void setUser(UserEntity user){
        this.user = user;
    }

    public long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public long getAmount() {
        return amount;
    }

    public PointType getType() {
        return type;
    }
}
