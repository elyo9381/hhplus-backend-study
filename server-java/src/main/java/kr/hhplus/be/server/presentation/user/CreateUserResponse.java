package kr.hhplus.be.server.presentation.user;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateUserResponse(UUID id, String email, String name, LocalDateTime createdAt) {
}
