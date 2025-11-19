# Infrastructure Architecture

## System Architecture

```mermaid
graph TB
    ClientApp[Client Application]
    
    GHR[GitHub Repository]
    GHA[GitHub Actions]
    DockerHub[Docker Hub/Registry]
    
    Redis[Redis Cache]
    App[Spring Boot Application<br/>Monolithic + DDD]
    MySQL[(MySQL)]
    LogServer[External Log Server<br/>ELK / Commercial Service]
    
    ClientApp -->|HTTP/HTTPS| Redis
    Redis -->|Cache Miss| App
    App -->|Query/Command| MySQL
    App -->|Business Event Logs| LogServer
    
    GHR -->|Push/PR| GHA
    GHA -->|Build & Test| DockerHub
    DockerHub -->|Pull Image| App
    
    style App fill:#e1f5ff
    style Redis fill:#ffebee
    style MySQL fill:#fff3e0
    style LogServer fill:#f3e5f5
    style GHA fill:#e8f5e9
```

## Component Details

### Application Layer (DDD)
```mermaid
graph LR
    subgraph "Spring Boot Application"
        subgraph "Presentation Layer"
            API[REST API Controllers]
        end
        
        subgraph "Application Layer"
            AuthApp[Auth Application]
            UserApp[User Application]
            ProductApp[Product Application]
            OrderApp[Order Application]
            PaymentApp[Payment Application]
            PointApp[Point Application]
            CouponApp[Coupon Application]
        end
        
        subgraph "Domain Layer"
            UserDomain[User Domain]
            ProductDomain[Product Domain]
            OrderDomain[Order Domain]
            PaymentDomain[Payment Domain]
            PointDomain[Point Domain]
            CouponDomain[Coupon Domain]
        end
        
        subgraph "Infrastructure Layer"
            JPA[JPA Repositories]
            RedisRepo[Redis Repository]
            LogClient[Log Client]
        end
    end
    
    API --> AuthApp
    API --> UserApp
    API --> ProductApp
    API --> OrderApp
    API --> PaymentApp
    API --> PointApp
    API --> CouponApp
    
    AuthApp --> UserDomain
    UserApp --> UserDomain
    ProductApp --> ProductDomain
    OrderApp --> OrderDomain
    OrderApp --> ProductDomain
    PaymentApp --> PaymentDomain
    PointApp --> PointDomain
    CouponApp --> CouponDomain
    
    UserDomain --> JPA
    ProductDomain --> JPA
    OrderDomain --> JPA
    PaymentDomain --> JPA
    PointDomain --> JPA
    CouponDomain --> JPA
    
    ProductApp --> RedisRepo
    OrderApp --> LogClient
    PaymentApp --> LogClient
```

## Request Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant R as Redis Cache
    participant A as Application
    participant M as MySQL
    participant L as Log Server
    
    C->>R: Request (상품 조회)
    alt Cache Hit
        R-->>C: Cached Response
    else Cache Miss
        R->>A: Forward Request
        A->>M: Query Data
        M-->>A: Return Data
        A->>R: Store Cache
        A-->>C: Response
    end
    
    Note over A,L: 비즈니스 이벤트 발생 시
    C->>A: Request (주문 완료)
    A->>M: Save Order
    M-->>A: Success
    A->>L: Send Event Log (Async)
    A-->>C: Response
```

## Deployment Flow

```mermaid
graph LR
    Dev[Developer]
    Repo[GitHub Repository]
    Actions[GitHub Actions]
    Build[Build JAR]
    Test[Run Tests]
    DockerBuild[Build Docker Image]
    Registry[Docker Registry]
    Deploy[Deploy Container]
    Server[Application Server]
    
    Dev -->|Push Code| Repo
    Repo -->|Trigger| Actions
    Actions --> Build
    Build --> Test
    Test --> DockerBuild
    DockerBuild -->|Push Image| Registry
    Registry -->|Pull Image| Deploy
    Deploy --> Server
    
    style Actions fill:#e8f5e9
    style DockerBuild fill:#e1f5ff
    style Server fill:#fff3e0
```

## Technology Stack

### Application
- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **Architecture**: Monolithic + DDD
- **ORM**: Spring Data JPA
- **Build**: Gradle

### Database
- **Primary DB**: MySQL 8.0
- **Cache**: Redis 7.x

### Infrastructure
- **Container**: Docker
- **CI/CD**: GitHub Actions
- **Logging**: ELK Stack / External Log Service

### Key Features
- **Caching Strategy**: Redis for product catalog, user sessions
- **Async Logging**: Non-blocking event logging to external service
- **Transaction Management**: Spring @Transactional
- **Concurrency Control**: Optimistic/Pessimistic Locking
