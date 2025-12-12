package kr.hhplus.be.server.point;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/points")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping("/{userId}")
    public PointResponse getPoints(@PathVariable UUID userId) {
        Long balance = pointService.getAvailablePoints(userId);
        return PointResponse.of(userId, balance);
    }

    @PostMapping("/{userId}/charge")
    public PointResponse chargePoint(
            @PathVariable UUID userId,
            @RequestBody ChargePointRequest request
    ) {
        pointService.chargePoint(userId, request.amount());
        Long balance = pointService.getAvailablePoints(userId);
        return PointResponse.of(userId, balance);
    }
}
