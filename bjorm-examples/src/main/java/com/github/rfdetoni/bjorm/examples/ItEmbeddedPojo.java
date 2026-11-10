package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Embedded;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_it_embedded_pojos")
public class ItEmbeddedPojo {
    @Id private UUID id;
    private String label;
    @Embedded private ItAddress billing;
    public ItEmbeddedPojo() {}
    public UUID getId(){return id;}
    public void setId(UUID value){id=value;}
    public String getLabel(){return label;}
    public void setLabel(String value){label=value;}
    public ItAddress getBilling(){return billing;}
    public void setBilling(ItAddress value){billing=value;}
}
