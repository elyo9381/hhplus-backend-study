package kr.hhplus.be.server.domain.user;

import kr.hhplus.be.server.entity.user.UserEntity;

public class UserMapper {

    public static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getName(),
                entity.getCreatedAt()
        );
    }

    public static UserEntity toEntity(User domain) {
        return new UserEntity(
                domain.getEmail(),
                domain.getPassword(),
                domain.getName()
        );
    }
}
