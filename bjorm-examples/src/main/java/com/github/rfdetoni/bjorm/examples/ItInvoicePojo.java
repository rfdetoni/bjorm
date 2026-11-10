package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.ManyToOne;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_it_invoices")
public class ItInvoicePojo {
    @Id private UUID id;
    private UUID customerId;
    private String reference;
    @ManyToOne(mappedBy="customerId") private ItUser customer;

    public ItInvoicePojo() {}
    public UUID getId() {return id;}
    public void setId(UUID value) {id=value;}
    public UUID getCustomerId() {return customerId;}
    public void setCustomerId(UUID value) {customerId=value;}
    public String getReference() {return reference;}
    public void setReference(String value) {reference=value;}
    public ItUser getCustomer() {return customer;}
    public void setCustomer(ItUser value) {customer=value;}
}
