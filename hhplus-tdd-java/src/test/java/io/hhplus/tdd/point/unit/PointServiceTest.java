package io.hhplus.tdd.point.unit;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 서비스의 흐름을 파악 할수 있는 유닛 테스트 작성
 */
@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private  PointHistoryTable pointHistoryTable;
    @Mock
    private  UserPointTable userPointTable;
    @InjectMocks
    private PointService service;

    private long CURRENT_MILLIES;

    @BeforeEach
    void each(){
        long CURRENT_MILLIES = System.currentTimeMillis();
    }

    @Test
    void retrievPointsTest(){
        //given
        long userId = 1L;
        long amount = 1000L;
        UserPoint expectedUserPoint = new UserPoint(userId, amount, CURRENT_MILLIES);
        when(userPointTable.selectById(userId)).thenReturn(expectedUserPoint);

        //when
        UserPoint point = service.retrievPoints(userId);

        //than
        assertThat(point).isEqualTo(expectedUserPoint);
        verify(userPointTable).selectById(userId);
    }


    @Test
    void retrievePointHistoriesTest(){

        //given
        long userId = 1L;
        long amount = 1000L;
        PointHistory expectedPointHistory = new PointHistory(1, userId, amount, TransactionType.CHARGE, CURRENT_MILLIES);
        List<PointHistory> expectedPointHistories = List.of(expectedPointHistory);
        when(userPointTable.selectById(userId)).thenReturn( UserPoint.empty(userId) );
        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expectedPointHistories);

        //when
        List<PointHistory> history = service.retrievePointHistroies(userId);

        //than
        assertThat(history).isEqualTo(expectedPointHistories);
        verify(pointHistoryTable).selectAllByUserId(userId);
    }

    @Test
    void chargePointTest(){
        //given
        long userId = 1L;
        long amount = 1000L;

        UserPoint emptyUserPoint = UserPoint.empty(userId);
        UserPoint calculatedUserPoint = emptyUserPoint.calculatePoint(amount, TransactionType.CHARGE);

        UserPoint expectedUserPoint = new UserPoint(calculatedUserPoint.id(), calculatedUserPoint.point(), CURRENT_MILLIES);
        PointHistory expectedPointHistory = new PointHistory(1, userId, amount, TransactionType.CHARGE, CURRENT_MILLIES);

        when(userPointTable.selectById(userId)).thenReturn(emptyUserPoint);
        when(userPointTable.insertOrUpdate(userId,calculatedUserPoint.point())).thenReturn(expectedUserPoint);
        when(pointHistoryTable.insert(userId,amount,TransactionType.CHARGE , CURRENT_MILLIES)).thenReturn(expectedPointHistory);

        //when
        UserPoint userPoint = service.charge(userId,amount);

        //than
        assertThat(userPoint.point()).isEqualTo(calculatedUserPoint.point());
        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId,calculatedUserPoint.point());
        verify(pointHistoryTable).insert(userId,amount,TransactionType.CHARGE , CURRENT_MILLIES);
    }

    @Test
    void usePointTest(){
        //given
        long userId = 1L;
        long amount = 1000L;

        UserPoint savedUserPoint = new UserPoint(userId, amount, CURRENT_MILLIES);
        UserPoint calculatedUserPoint = savedUserPoint.calculatePoint(amount, TransactionType.USE);

        UserPoint expectedUserPoint = new UserPoint(userId, calculatedUserPoint.point(), CURRENT_MILLIES);
        PointHistory expectedPointHistory = new PointHistory(1, userId, amount, TransactionType.USE, CURRENT_MILLIES);

        when(userPointTable.selectById(userId)).thenReturn(savedUserPoint);
        when(userPointTable.insertOrUpdate(userId,calculatedUserPoint.point())).thenReturn(expectedUserPoint);
        when(pointHistoryTable.insert(userId,amount,TransactionType.USE , CURRENT_MILLIES)).thenReturn(expectedPointHistory);

        //when
        UserPoint userPoint = service.use(userId,amount);

        //than
        assertThat(userPoint.point()).isEqualTo(calculatedUserPoint.point());
        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId,calculatedUserPoint.point());
        verify(pointHistoryTable).insert(userId,amount,TransactionType.USE , CURRENT_MILLIES);
    }

}