package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extensions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private  PointHistoryTable pointHistoryTable;
    @Mock
    private  UserPointTable userPointTable;
    @InjectMocks
    private  PointService service;

    private final long CURRENT_MILLIES = System.currentTimeMillis();

    @Test
    void retrievPointsTest(){
        //given
        long userId = 1;
        UserPoint expectedUserPoint = new UserPoint(userId, 1000, CURRENT_MILLIES);
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
        long userId = 1;
        PointHistory expectedPointHistory = new PointHistory(1, 1, 1000, TransactionType.CHARGE, CURRENT_MILLIES);
        List<PointHistory> expectedPointHistories = List.of(expectedPointHistory);
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

        UserPoint empty = UserPoint.empty(userId);
        long currentMillies = CURRENT_MILLIES;
        UserPoint expectedUserPoint = new UserPoint(userId, amount, currentMillies);
        PointHistory expectedPointHistory = new PointHistory(1, 1, 1000, TransactionType.CHARGE, currentMillies);

        when(userPointTable.selectById(userId)).thenReturn(empty);
        when(userPointTable.insertOrUpdate(userId,amount)).thenReturn(expectedUserPoint);
        when(pointHistoryTable.insert(userId,amount,TransactionType.CHARGE , currentMillies)).thenReturn(expectedPointHistory);

        //when
        UserPoint userPoint = userPointTable.insertOrUpdate(userId, amount);

        //than
        assertThat(userPoint).isEqualTo(expectedUserPoint);
        verify(userPointTable).insertOrUpdate(userId,amount);
    }

    @Test
    void usePointTest(){
        //given
        long userId = 1L;
        long amount = 1000L;

        long currentMillies = CURRENT_MILLIES;
        UserPoint savedUserPoint = new UserPoint(userId, amount, currentMillies);

        long calculated = savedUserPoint.point() - amount;
        UserPoint expectedUserPoint = new UserPoint(userId, savedUserPoint.point() - amount, currentMillies);
        PointHistory expectedPointHistory = new PointHistory(1, userId, 1000, TransactionType.USE, currentMillies);

        when(userPointTable.selectById(userId)).thenReturn(savedUserPoint);
        when(userPointTable.insertOrUpdate(userId,calculated)).thenReturn(expectedUserPoint);
        when(pointHistoryTable.insert(userId,calculated,TransactionType.USE , currentMillies)).thenReturn(expectedPointHistory);

        //when
        UserPoint userPoint = userPointTable.insertOrUpdate(userId, calculated);

        //than
        assertThat(userPoint).isEqualTo(expectedUserPoint);
        verify(userPointTable).insertOrUpdate(userId,calculated);
    }

}