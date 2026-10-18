package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;

class ItInheritedFkBase {
    private UUID parentId;
    public UUID getParentId(){return parentId;}
    public void setParentId(UUID value){parentId=value;}
}

@Table("bjorm_it_inherited_fk_children")
public class ItInheritedFkChild extends ItInheritedFkBase {
    @Id private UUID id;
    private String name;
    public ItInheritedFkChild(){}
    public UUID getId(){return id;}
    public void setId(UUID value){id=value;}
    public String getName(){return name;}
    public void setName(String value){name=value;}
}
