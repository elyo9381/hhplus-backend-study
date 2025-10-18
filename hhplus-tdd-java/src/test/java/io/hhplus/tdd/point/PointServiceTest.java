package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PointServiceTest {


    @Test
    void retrievPointsTest(){
        //given
        long userId = 1;
        PointService service = new PointService();

        //when
        UserPoint point = service.retrievPoints(userId);

        //than
        assertThat(point).isNull();
    }


    @Test
    void retrievePointHistoriesTest(){

        //given
        long userId = 1;
        PointService service = new PointService();

        //when
        List<PointHistory> history = service.retrievePointHistroies(userId);

        //than
        assertThat(history).isNull();
    }

    @Test
    void chargePointTest(){

        //given
        long userId = 1L;
        long amount = 1000L;
        PointService pointService = new PointService();

        //when
        UserPoint charge = pointService.charge(userId , amount);

        //than
        assertThat(charge).isNull();
    }

    @Test
    void usePointTest(){
        //given
        long userId = 1L;
        long amount = 1000L;

        PointService pointService = new PointService();
        //when
        UserPoint use = pointService.use(userId, amount);

        //than
        assertThat(use).isNull();

    }

}