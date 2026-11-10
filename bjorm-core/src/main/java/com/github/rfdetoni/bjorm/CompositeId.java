package com.github.rfdetoni.bjorm;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Ordered, immutable primary-key parts for an entity with multiple @Id fields.
 * Component order matches declaration order in the compiled entity.
 */
public record CompositeId(List<Object> parts) {
    public CompositeId {
        Objects.requireNonNull(parts,"parts");
        parts=List.copyOf(parts);
        if(parts.size()<2)throw new IllegalArgumentException("Composite key requires at least two parts");
    }
    public static CompositeId of(Object... parts) {
        Objects.requireNonNull(parts,"parts");
        return new CompositeId(Arrays.asList(parts.clone()));
    }
    public Object part(int index) {return parts.get(index);}
}
