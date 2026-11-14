package com.github.rfdetoni.bjorm.spring;

import java.util.Objects;

/** Opaque, stable keyset position: last sort value plus the last primary key. */
public record SortedCursor(Object sortValue,Object id) {
    public SortedCursor {
        Objects.requireNonNull(sortValue,"sortValue (NULL sorting needs an explicit strategy)");
        Objects.requireNonNull(id,"id");
    }
}
