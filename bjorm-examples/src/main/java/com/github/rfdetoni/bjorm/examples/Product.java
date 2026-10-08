package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.math.BigDecimal;
import java.util.UUID;
/** Mutable POJO example with optimistic versioning. */
@Table("products")
public class Product implements ActiveRecord<Product> {
    @Id private UUID id;
    private String name;
    @Column("unit_price") private BigDecimal price;
    private Status status;
    @Version private int version;
    public Product() {}
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal price){this.price=price;}
    public Status getStatus(){return status;} public void setStatus(Status status){this.status=status;}
    public int getVersion(){return version;} public void setVersion(int version){this.version=version;}
}
