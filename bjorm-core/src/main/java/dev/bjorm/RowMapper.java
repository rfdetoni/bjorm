package dev.bjorm;
import java.sql.*;
@FunctionalInterface
public interface RowMapper<T> { T read(ResultSet result) throws SQLException; }
