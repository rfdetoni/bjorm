package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.JoinType;
import com.github.rfdetoni.bjorm.ManyToOne;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_it_invoices")
public record ItInvoiceInner(@Id UUID id,UUID customerId,String reference,
    @ManyToOne(mappedBy="customerId",type=JoinType.INNER) ItUser customer) {}
