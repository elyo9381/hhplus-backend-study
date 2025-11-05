package kr.hhplus.be.server.api.point;

import kr.hhplus.be.server.entity.point.UserPointEntity;
import kr.hhplus.be.server.entity.point.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final UserPointRepository userPointRepository;

    @GetMapping("/{userId}")
    public UserPointEntity getPoint(@PathVariable UUID userId) {
        return userPointRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPointEntity newPoint = new UserPointEntity(userId);
                    return userPointRepository.save(newPoint);
                });
    }

    @PostMapping("/{userId}/charge")
    @Transactional
    public UserPointEntity chargePoint(@PathVariable UUID userId, @RequestBody ChargePointRequest request) {
        UserPointEntity userPoint = userPointRepository.findByUserId(userId)
                .orElseGet(() -> new UserPointEntity(userId));

        userPoint.charge(request.getAmount());
        return userPointRepository.save(userPoint);
    }
}
