package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.JdbcColumnConverter;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Pure, thread-safe conversion; no reflection, state or ORM dependencies. */
public final class MoneyValueConverter implements JdbcColumnConverter<MoneyValue> {
    @Override public Object write(MoneyValue value) {
        return value.amount();
    }
    @Override public MoneyValue read(ResultSet rs,int columnIndex) throws SQLException {
        var amount=rs.getBigDecimal(columnIndex);
        return amount==null?null:new MoneyValue(amount);
    }
}
