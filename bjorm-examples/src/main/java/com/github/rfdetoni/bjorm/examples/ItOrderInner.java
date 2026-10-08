package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.*;

@Table("bjorm_it_orders")
public class ItOrderInner {
    @Id private UUID id;
    private String description;
    @Children(mappedBy="orderId",type=JoinType.INNER) private List<ItOrderNote> notes=new ArrayList<>();
    public ItOrderInner(){}
    public UUID getId(){return id;}
    public void setId(UUID value){id=value;}
    public String getDescription(){return description;}
    public void setDescription(String value){description=value;}
    public List<ItOrderNote> getNotes(){return notes;}
    public void setNotes(List<ItOrderNote> value){notes=value;}
}
