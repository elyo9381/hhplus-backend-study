package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.apache.catalina.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/point")
public class PointController {

    private final long CURRENT_MILLIES = System.currentTimeMillis();
    private PointHistoryTable pointHistoryTable;
    private UserPointTable userPointTable;

    public PointController() {
        this.pointHistoryTable = new PointHistoryTable();
        this.userPointTable = new UserPointTable();
    }

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}")
    public UserPoint point(
            @PathVariable long id
    ) {
        return new UserPoint(0, 0, 0);
    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(
            @PathVariable long id
    ) {
        return List.of();
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        UserPoint result  = userPointTable.insertOrUpdate(id, amount);
        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, CURRENT_MILLIES);
        return result;
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        UserPoint userPoint = userPointTable.selectById(id);

        // 포인트 검사하기
        if(!userPoint.isGreatThanZero()) throw new RuntimeException("0보다 작으면 안됩니다.");

        long calculatedAmout = userPoint.point() - amount;
        UserPoint result = userPointTable.insertOrUpdate(userPoint.id(), calculatedAmout);
        pointHistoryTable.insert(userPoint.id(), userPoint.point(), TransactionType.USE , result.updateMillis());

        return result;
    }
}
