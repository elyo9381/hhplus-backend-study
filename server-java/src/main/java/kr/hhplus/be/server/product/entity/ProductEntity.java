package kr.hhplus.be.server.product.entity;

import ch.qos.logback.core.joran.action.ActionUtil;
import jakarta.persistence.*;

@Entity
@Table(name = "product")
public class ProductEntity {

    protected ProductEntity(){}

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column
    private String name;

    @Column
    private long price;

    @Column
    private long inventoryCount;

    @Enumerated(value = EnumType.STRING)
    private ProductType type ;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPrice() {
        return price;
    }

    public long getInventoryCount() {
        return inventoryCount;
    }

    public ProductType getType() {
        return type;
    }
}
