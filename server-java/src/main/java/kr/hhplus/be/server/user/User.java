package kr.hhplus.be.server.user;

import java.time.LocalDateTime;
import java.util.UUID;

public class User {
    private final UUID id;
    private final String email;
    private final String name;
    private final LocalDateTime createdAt;

    public User(UUID id, String email, String name, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.createdAt = createdAt;
    }

    public User(String email, String name) {
        this(UUID.randomUUID(), email, name, LocalDateTime.now());
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
