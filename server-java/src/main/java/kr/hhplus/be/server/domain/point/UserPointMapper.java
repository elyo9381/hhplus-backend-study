package kr.hhplus.be.server.domain.point;

import kr.hhplus.be.server.entity.point.UserPointEntity;
import kr.hhplus.be.server.entity.user.UserEntity;

public class UserPointMapper {

    public static UserPoint toDomain(UserPointEntity entity) {
        return new UserPoint(
                entity.getId(),
                entity.getUser().getId(),
                entity.getAmount(),
                entity.getCreatedAt(),
                entity.getExpiredAt()
        );
    }

    public static UserPointEntity toEntity(UserPoint domain, UserEntity user) {
        return new UserPointEntity(user);
    }
}
