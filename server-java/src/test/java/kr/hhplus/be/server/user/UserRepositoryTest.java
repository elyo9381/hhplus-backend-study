package kr.hhplus.be.server.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUser() {
        // given
        UserEntity entity = new UserEntity("test@example.com", "Test User");

        // when
        UserEntity savedEntity = userRepository.save(entity);
        Optional<UserEntity> foundEntity = userRepository.findById(savedEntity.getId());

        // then
        assertThat(foundEntity).isPresent();
        assertThat(foundEntity.get().getEmail()).isEqualTo("test@example.com");
        assertThat(foundEntity.get().getName()).isEqualTo("Test User");
        assertThat(foundEntity.get().getCreatedAt()).isNotNull();
    }
}
