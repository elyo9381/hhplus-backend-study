package kr.hhplus.be.server.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(String email, String name) {
        UserEntity entity = new UserEntity(email, name);
        UserEntity savedEntity = userRepository.save(entity);
        return savedEntity.toDomain();
    }

    @Transactional(readOnly = true)
    public User getUser(UUID id) {
        return userRepository.findById(id)
                .map(UserEntity::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
