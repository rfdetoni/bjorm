package com.github.rfdetoni.bjorm;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Compile-time generated column mapping contract. A generated mapper calls this
 * directly, never via reflection. Instances are shared and must be thread-safe.
 *
 * The domain type is recommended to be immutable. Both methods must handle
 * SQL null safely; a null domain value is bound as JDBC NULL by the mapper.
 */
public interface JdbcColumnConverter<T> {
    Object write(T value);
    T read(ResultSet resultSet, int columnIndex) throws SQLException;
}
