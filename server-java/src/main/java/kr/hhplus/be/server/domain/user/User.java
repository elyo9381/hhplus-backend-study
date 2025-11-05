package kr.hhplus.be.server.domain.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class User {
    private UUID id;
    private String email;
    private String password;
    private String name;
    private LocalDateTime createdAt;

    public static User create(String email, String password, String name) {
        return new User(
                UUID.randomUUID(),
                email,
                password,
                name,
                LocalDateTime.now()
        );
    }
}
