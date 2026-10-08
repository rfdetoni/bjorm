package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Children;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Parent graph fixture: insert/upsert children in FK order, delete children first. */
@Table("bjorm_it_orders")
public class ItOrder {
    @Id(uuidV7 = true) private UUID id;
    private String description;
    @Children(mappedBy = "orderId") private List<ItOrderLine> lines = new ArrayList<>();
    @Children(mappedBy = "orderId") private List<ItOrderNote> notes = new ArrayList<>();

    public ItOrder() {}
    public List<ItOrderNote> getNotes() { return notes; }
    public void setNotes(List<ItOrderNote> notes) { this.notes = notes; }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<ItOrderLine> getLines() { return lines; }
    public void setLines(List<ItOrderLine> lines) { this.lines = lines; }
}
