package com.github.rfdetoni.bjorm;
import java.sql.*;
@FunctionalInterface
public interface StatementBinder {
    StatementBinder NONE = stmt -> {};
    void bind(PreparedStatement statement) throws SQLException;
}
