package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("bjorm_it_order_notes")
public class ItOrderNote {
    @Id private UUID id;
    private UUID orderId;
    private String note;
    @Version private int version;
    public ItOrderNote() {}
    public UUID getId(){return id;}
    public void setId(UUID value){id=value;}
    public UUID getOrderId(){return orderId;}
    public void setOrderId(UUID value){orderId=value;}
    public int getVersion(){return version;}
    public void setVersion(int value){version=value;}
    public String getNote(){return note;}
    public void setNote(String value){note=value;}
}
