package com.github.rfdetoni.bjorm.examples;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable non-JDBC domain value; stored in one DECIMAL column. */
public record MoneyValue(BigDecimal amount) {
    public MoneyValue {
        Objects.requireNonNull(amount,"amount");
    }
}
