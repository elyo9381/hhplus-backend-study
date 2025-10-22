package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {

    private static final Logger log = LoggerFactory.getLogger(PointService.class);

    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;


    public UserPoint retrievPoints(long id) {
        return userPointTable.selectById(id);
    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    public List<PointHistory> retrievePointHistroies(long id) {
        // 유저 조회
        UserPoint userPoint = userPointTable.selectById(id);

        List<PointHistory> pointHistories = pointHistoryTable.selectAllByUserId(userPoint.id());

        return pointHistories;
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    public UserPoint charge(long id, long amount) {
        return updatePoint(id, amount, TransactionType.CHARGE);
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    public UserPoint use(long id, long amount) {
        return updatePoint(id, amount, TransactionType.USE);
    }

    private UserPoint updatePoint(long id, long amount, TransactionType type) {
        UserPoint findUserPoint = userPointTable.selectById(id);

        UserPoint calculatedPoint = findUserPoint.calculatePoint(amount, type);

        UserPoint updateUserPoint = userPointTable.insertOrUpdate(calculatedPoint.id(), calculatedPoint.point());
        pointHistoryTable.insert(id, amount, type, updateUserPoint.updateMillis());

        return updateUserPoint;
    }
}
