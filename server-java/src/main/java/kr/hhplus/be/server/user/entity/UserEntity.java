package kr.hhplus.be.server.user.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user")
public class UserEntity {

    protected UserEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id ;

    @Column
    private String name;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserPointEntity> userPointEntities = new ArrayList<>();

    public void addUserPoint(UserPointEntity userPointEntity){
        this.userPointEntities.add(userPointEntity);
        userPointEntity.setUser(this);
    }

    public void removeUserPoint(UserPointEntity userPointEntity){
        this.userPointEntities.remove(userPointEntity);
        userPointEntity.setUser(null);
    }


    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<UserPointEntity> getUserPointEntities() {
        return userPointEntities;
    }
}
