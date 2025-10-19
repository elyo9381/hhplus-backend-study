package io.hhplus.tdd.point;

public record UserPoint(
        long id,
        long point,
        long updateMillis
) {

    public static UserPoint empty(long id) {
        return new UserPoint(id, 0, System.currentTimeMillis());
    }

    public UserPoint calculatePoint(long amount,TransactionType type){
        long calculatedPoint = switch (type) {
            case CHARGE -> this.point() + amount;
            case USE -> this.point() - amount;
        };

        if (calculatedPoint < 0) throw new IllegalArgumentException("0보다 작을수는 없습니다.");

        return new UserPoint(this.id, calculatedPoint, System.currentTimeMillis());
    }
}
