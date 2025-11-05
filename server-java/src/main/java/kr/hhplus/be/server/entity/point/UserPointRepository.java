package kr.hhplus.be.server.entity.point;

import kr.hhplus.be.server.entity.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserPointRepository extends JpaRepository<UserPointEntity, UUID> {
    Optional<UserPointEntity> findByUser(UserEntity user);
    Optional<UserPointEntity> findByUserId(UUID userId);
}
