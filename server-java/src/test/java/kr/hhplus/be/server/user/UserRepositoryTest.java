package kr.hhplus.be.server.user;

import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.user.persistence.UserRepository;
import kr.hhplus.be.server.infrastructure.user.persistence.UserRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(UserRepositoryImpl.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUser() {
        // given
        User user = new User("test@example.com", "Test User");

        // when
        User savedUser = userRepository.save(user);
        Optional<User> foundUser = userRepository.findById(savedUser.getId());

        // then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(foundUser.get().getName()).isEqualTo("Test User");
        assertThat(foundUser.get().getCreatedAt()).isNotNull();
    }
}
