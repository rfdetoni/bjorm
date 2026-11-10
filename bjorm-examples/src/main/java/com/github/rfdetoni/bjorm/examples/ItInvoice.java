package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.ManyToOne;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

/** Record reference fixture: customer is populated via one eager LEFT JOIN. */
@Table("bjorm_it_invoices")
public record ItInvoice(@Id UUID id,UUID customerId,String reference,
    @ManyToOne(mappedBy="customerId") ItUser customer) {}
