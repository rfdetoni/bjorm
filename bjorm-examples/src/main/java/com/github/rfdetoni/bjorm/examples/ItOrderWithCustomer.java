package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Children;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.ManyToOne;
import com.github.rfdetoni.bjorm.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Root graph using both to-many and to-one JOINs in a single JDBC query. */
@Table("bjorm_it_orders")
public class ItOrderWithCustomer {
    @Id private UUID id;
    private UUID customerId;
    private String description;
    @Children(mappedBy="orderId") private List<ItOrderLine> lines=new ArrayList<>();
    @ManyToOne(mappedBy="customerId") private ItUser customer;

    public ItOrderWithCustomer() {}
    public UUID getId(){return id;}
    public void setId(UUID id){this.id=id;}
    public UUID getCustomerId(){return customerId;}
    public void setCustomerId(UUID customerId){this.customerId=customerId;}
    public String getDescription(){return description;}
    public void setDescription(String description){this.description=description;}
    public List<ItOrderLine> getLines(){return lines;}
    public void setLines(List<ItOrderLine> lines){this.lines=lines;}
    public ItUser getCustomer(){return customer;}
    public void setCustomer(ItUser customer){this.customer=customer;}
}
