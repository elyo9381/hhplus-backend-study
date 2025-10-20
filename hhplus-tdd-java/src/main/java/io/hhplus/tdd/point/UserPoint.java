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
        if(amount < 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다");
        }

        long calculatedPoint = switch (type) {
            case CHARGE -> this.point() + amount;
            case USE -> {
                if(this.point < amount) {
                    throw new IllegalArgumentException("포인트가 부족합니다.");
                }
                yield this.point() - amount;
            }
        };

        if (calculatedPoint < 0) throw new IllegalArgumentException("계산된 포인트가 0보다 작을수는 없습니다.");

        return new UserPoint(this.id, calculatedPoint, System.currentTimeMillis());
    }
}
