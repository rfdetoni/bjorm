package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.*;
import java.util.UUID;

@Table("bjorm_it_composite_pojo")
public class ItCompositePojo {
    @Id(strategy=IdStrategy.ASSIGNED) private UUID tenantId;
    @Id(strategy=IdStrategy.ASSIGNED) private String sku;
    private String label;
    public ItCompositePojo() {}
    public UUID getTenantId(){return tenantId;}
    public void setTenantId(UUID tenantId){this.tenantId=tenantId;}
    public String getSku(){return sku;}
    public void setSku(String sku){this.sku=sku;}
    public String getLabel(){return label;}
    public void setLabel(String label){this.label=label;}
}
