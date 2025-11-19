# Coupon Sequence Diagrams

## 1. 쿠폰 발급 (Issue Coupon)

```mermaid
sequenceDiagram
    actor User
    participant API as API Gateway
    participant CouponService as Coupon Service
    participant DB as MySQL
    participant Cache as Redis
    
    User->>API: POST /api/coupons/{couponId}/issue
    
    API->>CouponService: issueCoupon(userId, couponId)
    
    Note over CouponService: Transaction Start
    
    CouponService->>DB: SELECT * FROM coupon<br/>WHERE id = ?<br/>FOR UPDATE
    
    alt Coupon not found or inactive
        DB-->>CouponService: Invalid coupon
        Note over CouponService: Transaction Rollback
        CouponService-->>API: 404 Not Found
        API-->>User: Error: Coupon not found
    else Coupon valid
        DB-->>CouponService: Coupon data
        
        CouponService->>CouponService: validateCoupon()
        
        alt Coupon expired or exhausted
            CouponService-->>API: 400 Bad Request
            API-->>User: Error: Coupon unavailable
        else Coupon available
            CouponService->>DB: SELECT * FROM user_coupon<br/>WHERE user_id = ? AND coupon_id = ?<br/>FOR UPDATE
            
            alt Already issued
                DB-->>CouponService: Existing coupon
                Note over CouponService: Transaction Rollback
                CouponService-->>API: 400 Bad Request
                API-->>User: Error: Already issued
            else Not issued yet
                DB-->>CouponService: No record
                
                CouponService->>DB: UPDATE coupon<br/>SET issued_quantity = issued_quantity + 1<br/>WHERE id = ?<br/>AND issued_quantity < total_quantity
                
                alt Update failed (race condition)
                    DB-->>CouponService: 0 rows updated
                    Note over CouponService: Transaction Rollback
                    CouponService-->>API: 409 Conflict
                    API-->>User: Error: Coupon sold out
                else Update success
                    DB-->>CouponService: Updated
                    
                    CouponService->>DB: INSERT INTO user_coupon<br/>(user_id, coupon_id,<br/>status='AVAILABLE',<br/>expired_at)
                    
                    CouponService->>Cache: DEL coupon:{couponId}
                    
                    Note over CouponService: Transaction Commit
                    
                    CouponService-->>API: 201 Created
                    API-->>User: {userCouponId, couponName, expiredAt}
                end
            end
        end
    end
```

---

## 2. 사용 가능한 쿠폰 조회 (Get Available Coupons)

```mermaid
sequenceDiagram
    actor User
    participant API as API Gateway
    participant CouponService as Coupon Service
    participant DB as MySQL
    
    User->>API: GET /api/coupons/available
    
    API->>CouponService: getAvailableCoupons(userId)
    CouponService->>DB: SELECT c.*, uc.id as user_coupon_id<br/>FROM coupon c<br/>LEFT JOIN user_coupon uc<br/>ON c.id = uc.coupon_id<br/>AND uc.user_id = ?<br/>WHERE c.status = 'ACTIVE'<br/>AND c.start_date <= NOW()<br/>AND c.end_date >= NOW()<br/>AND c.issued_quantity < c.total_quantity
    
    DB-->>CouponService: Coupon list
    CouponService-->>API: 200 OK
    API-->>User: {coupons[], isIssued, remaining}
```

---

## 3. 내 쿠폰 조회 (Get My Coupons)

```mermaid
sequenceDiagram
    actor User
    participant API as API Gateway
    participant CouponService as Coupon Service
    participant DB as MySQL
    
    User->>API: GET /api/coupons/my?status=AVAILABLE
    
    API->>CouponService: getMyCoupons(userId, status)
    CouponService->>DB: SELECT uc.*, c.*<br/>FROM user_coupon uc<br/>JOIN coupon c ON uc.coupon_id = c.id<br/>WHERE uc.user_id = ?<br/>AND uc.status = ?<br/>AND uc.expired_at > NOW()<br/>ORDER BY uc.expired_at ASC
    
    DB-->>CouponService: User coupon list
    CouponService-->>API: 200 OK
    API-->>User: {coupons[], count}
```

---

## 4. 주문에 적용 가능한 쿠폰 조회 (Get Applicable Coupons for Order)

```mermaid
sequenceDiagram
    actor User
    participant API as API Gateway
    participant CouponService as Coupon Service
    participant ProductService as Product Service
    participant DB as MySQL
    
    User->>API: POST /api/coupons/applicable
    Note over User,API: {productIds[], totalAmount}
    
    API->>CouponService: getApplicableCoupons(userId, request)
    
    CouponService->>DB: SELECT uc.*, c.*<br/>FROM user_coupon uc<br/>JOIN coupon c ON uc.coupon_id = c.id<br/>WHERE uc.user_id = ?<br/>AND uc.status = 'AVAILABLE'<br/>AND uc.expired_at > NOW()
    
    DB-->>CouponService: User coupons
    
    loop For each coupon
        CouponService->>CouponService: validateMinOrderAmount(totalAmount)
        
        alt apply_type = ORDER
            CouponService->>CouponService: Applicable to order
        else apply_type = PRODUCT
            CouponService->>DB: SELECT * FROM coupon_product<br/>WHERE coupon_id = ?<br/>AND product_id IN (?)
            
            alt No matching products
                DB-->>CouponService: Empty
                CouponService->>CouponService: Not applicable
            else Has matching products
                DB-->>CouponService: Matching products
                CouponService->>CouponService: Applicable to products
            end
        end
        
        CouponService->>CouponService: calculateDiscount()
    end
    
    CouponService-->>API: 200 OK
    API-->>User: {applicableCoupons[], estimatedDiscount}
```

---

## 5. 선착순 쿠폰 발급 (Issue First-Come Coupon with Redis)

```mermaid
sequenceDiagram
    actor User
    participant API as API Gateway
    participant CouponService as Coupon Service
    participant Cache as Redis
    participant DB as MySQL
    
    User->>API: POST /api/coupons/{couponId}/issue
    
    API->>CouponService: issueCoupon(userId, couponId)
    
    CouponService->>Cache: GET coupon:issued:{couponId}
    Cache-->>CouponService: issued_count
    
    CouponService->>Cache: GET coupon:total:{couponId}
    Cache-->>CouponService: total_quantity
    
    alt Sold out (Redis check)
        CouponService-->>API: 409 Conflict
        API-->>User: Error: Coupon sold out
    else Available
        CouponService->>Cache: INCR coupon:issued:{couponId}
        Cache-->>CouponService: new_count
        
        alt Exceeded limit
            CouponService->>Cache: DECR coupon:issued:{couponId}
            CouponService-->>API: 409 Conflict
            API-->>User: Error: Coupon sold out
        else Within limit
            Note over CouponService: Transaction Start
            
            CouponService->>DB: INSERT INTO user_coupon<br/>(user_id, coupon_id, status='AVAILABLE')
            
            CouponService->>DB: UPDATE coupon<br/>SET issued_quantity = issued_quantity + 1
            
            alt DB transaction failed
                Note over CouponService: Transaction Rollback
                CouponService->>Cache: DECR coupon:issued:{couponId}
                Note over CouponService: Redis 보상 처리
                CouponService-->>API: 500 Internal Error
                API-->>User: Error: Coupon issuance failed
            else DB transaction success
                Note over CouponService: Transaction Commit
                
                CouponService-->>API: 201 Created
                API-->>User: {userCouponId, couponName}
            end
        end
    end
```
