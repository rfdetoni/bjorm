package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
@Table("bjorm_it_inherited")
public class InheritedPatient extends AuditedBase {
    private String name;
    public InheritedPatient(){}
    public String getName(){return name;}
    public void setName(String name){this.name=name;}
}
