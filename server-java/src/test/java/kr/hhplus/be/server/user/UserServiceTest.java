package kr.hhplus.be.server.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateUser() {
        // given
        String email = "test@example.com";
        String name = "Test User";
        User savedUser = new User(UUID.randomUUID(), email, name, java.time.LocalDateTime.now());
        
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // when
        User result = userService.createUser(email, name);

        // then
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getName()).isEqualTo(name);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldGetUser() {
        // given
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "test@example.com", "Test User", java.time.LocalDateTime.now());
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        User result = userService.getUser(userId);

        // then
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(userRepository).findById(userId);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }
}
