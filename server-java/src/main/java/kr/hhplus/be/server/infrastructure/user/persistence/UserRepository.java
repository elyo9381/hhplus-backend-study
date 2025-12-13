package kr.hhplus.be.server.infrastructure.user.persistence;

import kr.hhplus.be.server.domain.user.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
}
