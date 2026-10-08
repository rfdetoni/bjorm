package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.*;

/** Test fixture for RIGHT and FULL OUTER joined child collections. */
@Table("bjorm_it_orders")
public class ItOrderOuter {
    @Id private UUID id;
    private String description;
    @Children(mappedBy="orderId",type=JoinType.RIGHT) private List<ItOrderLine> lines=new ArrayList<>();
    @Children(mappedBy="orderId",type=JoinType.FULL) private List<ItOrderNote> notes=new ArrayList<>();
    public ItOrderOuter(){}
    public UUID getId(){return id;}
    public void setId(UUID value){id=value;}
    public String getDescription(){return description;}
    public void setDescription(String value){description=value;}
    public List<ItOrderLine> getLines(){return lines;}
    public void setLines(List<ItOrderLine> value){lines=value;}
    public List<ItOrderNote> getNotes(){return notes;}
    public void setNotes(List<ItOrderNote> value){notes=value;}
}
