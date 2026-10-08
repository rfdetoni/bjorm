package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
@Table("identities")
public class Identity {
    @Id(generated=true) private long id;
    private String label;
    public Identity() {}
    public long getId(){return id;}
    public void setId(long id){this.id=id;}
    public String getLabel(){return label;}
    public void setLabel(String label){this.label=label;}
}
