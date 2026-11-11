package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Column;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Json;
import com.github.rfdetoni.bjorm.Table;

import java.util.List;
import java.util.UUID;

/** Mutable JSON payload fixture: detached writes must reject shallow copies. */
@Table("bjorm_it_typed_json")
public class ItTypedJsonPojo {
    @Id private UUID id;
    @Column("values_json") @Json(codec=IntListCodec.class) private List<Integer> values;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public List<Integer> getValues() { return values; }
    public void setValues(List<Integer> values) { this.values = values; }
}
