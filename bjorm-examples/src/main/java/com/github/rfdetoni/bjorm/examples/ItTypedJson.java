package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.*;
@Table("bjorm_it_typed_json")
public record ItTypedJson(@Id UUID id,@Column("values_json") @Json(codec=IntListCodec.class) List<Integer> values) {}
