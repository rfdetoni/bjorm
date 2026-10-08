package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("bjorm_it_auto_uuid")
public class AutoUuidPojo {
    @Id private UUID id;
    private String label;
    public AutoUuidPojo(){}
    public UUID getId(){return id;}
    public void setId(UUID id){this.id=id;}
    public String getLabel(){return label;}
    public void setLabel(String label){this.label=label;}
}
