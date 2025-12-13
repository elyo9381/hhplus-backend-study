package kr.hhplus.be.server.infrastructure.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
}
