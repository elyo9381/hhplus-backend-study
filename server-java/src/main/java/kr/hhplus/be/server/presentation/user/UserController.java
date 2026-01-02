package kr.hhplus.be.server.presentation.user;

import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.application.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.email(), request.name());
        return new CreateUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getCreatedAt()
        );
    }

    @GetMapping("/api/users/{id}")
    public CreateUserResponse getUser(@PathVariable UUID id) {
        User user = userService.getUser(id);
        return new CreateUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getCreatedAt()
        );
    }
}
