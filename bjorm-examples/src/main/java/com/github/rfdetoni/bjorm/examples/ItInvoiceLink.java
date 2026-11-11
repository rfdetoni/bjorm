package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.ManyToOne;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

/** Link -> Invoice -> User uses a two-hop eager to-one tree in one query. */
@Table("bjorm_it_invoice_links")
public record ItInvoiceLink(@Id UUID id,UUID invoiceId,String label,
    @ManyToOne(mappedBy="invoiceId") ItInvoice invoice) {}
