# ERD - Multiple Coupons Support

## 여러 쿠폰 사용을 위한 ERD 수정안

### 변경 사항
- ORDER.user_coupon_id 제거
- ORDER_ITEM.user_coupon_id 제거
- ORDER_COUPON 테이블 추가 (주문 레벨 쿠폰)
- ORDER_ITEM_COUPON 테이블 추가 (상품 레벨 쿠폰)

---

## Mermaid ERD

```mermaid
erDiagram
    USER ||--o{ ORDER : "주문"
    USER ||--o{ USER_POINT : "포인트"
    USER ||--o{ POINT_HISTORY : "이력"
    USER ||--o{ USER_COUPON : "보유"
    
    PRODUCT ||--o{ ORDER_ITEM : "포함"
    PRODUCT ||--o{ COUPON_PRODUCT : "적용"
    
    ORDER ||--o{ ORDER_ITEM : "상품"
    ORDER ||--o{ ORDER_COUPON : "쿠폰사용"
    ORDER ||--o{ PAYMENT : "결제"
    
    ORDER_ITEM ||--o{ ORDER_ITEM_COUPON : "쿠폰사용"
    
    ORDER_COUPON }o--|| USER_COUPON : "참조"
    ORDER_ITEM_COUPON }o--|| USER_COUPON : "참조"
    
    COUPON ||--o{ USER_COUPON : "발급"
    COUPON ||--o{ COUPON_PRODUCT : "대상"
    
    ORDER {
        uuid id PK
        uuid user_id FK
        bigint total_amount
        bigint discount_amount
        bigint final_amount
        bigint paid_amount
        bigint point_amount
        bigint remaining_amount
        enum status "PENDING, PAID, CANCELLED, REFUNDED"
        timestamp created_at
        timestamp updated_at
    }
    
    ORDER_ITEM {
        uuid id PK
        uuid order_id FK
        uuid product_id FK
        string product_name
        bigint unit_price
        int quantity
        bigint total_price
        bigint discount_amount
        bigint final_price
    }
    
    ORDER_COUPON {
        uuid id PK
        uuid order_id FK
        uuid user_coupon_id FK
        bigint discount_amount
        timestamp created_at
    }
    
    ORDER_ITEM_COUPON {
        uuid id PK
        uuid order_item_id FK
        uuid user_coupon_id FK
        bigint discount_amount
        timestamp created_at
    }
```

---

## 사용 예시

### 시나리오: 주문 레벨 쿠폰 2개 + 상품 레벨 쿠폰 1개

```
ORDER (total: 100,000원)
├─ ORDER_COUPON 1: 전체 10% 할인 → -10,000원
├─ ORDER_COUPON 2: 5,000원 할인 → -5,000원
├─ ORDER_ITEM 1 (상품A: 50,000원)
│  └─ ORDER_ITEM_COUPON 1: 3,000원 할인 → -3,000원
└─ ORDER_ITEM 2 (상품B: 50,000원)

최종 금액: 100,000 - 10,000 - 5,000 - 3,000 = 82,000원
```

---

## 환불 시 쿠폰 복구

```sql
-- 주문 레벨 쿠폰 복구
UPDATE user_coupon uc
SET status = 'AVAILABLE', used_at = NULL
WHERE id IN (
    SELECT user_coupon_id 
    FROM order_coupon 
    WHERE order_id = ?
);

-- 상품 레벨 쿠폰 복구
UPDATE user_coupon uc
SET status = 'AVAILABLE', used_at = NULL
WHERE id IN (
    SELECT user_coupon_id 
    FROM order_item_coupon oic
    JOIN order_item oi ON oic.order_item_id = oi.id
    WHERE oi.order_id = ?
);
```
