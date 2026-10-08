package dev.bjorm;
import java.sql.*;
/** Mapping helpers only where JDBC primitive getters hide SQL NULL. */
public final class JdbcValues {
    private JdbcValues() {}
    public static int requiredInt(ResultSet rs, int i) throws SQLException { int v=rs.getInt(i); if(rs.wasNull())throw new SQLException("NULL for required int at column "+i); return v; }
    public static long requiredLong(ResultSet rs, int i) throws SQLException { long v=rs.getLong(i); if(rs.wasNull())throw new SQLException("NULL for required long at column "+i); return v; }
    public static boolean requiredBoolean(ResultSet rs, int i) throws SQLException { boolean v=rs.getBoolean(i); if(rs.wasNull())throw new SQLException("NULL for required boolean at column "+i); return v; }
    public static short requiredShort(ResultSet rs, int i) throws SQLException { short v=rs.getShort(i); if(rs.wasNull())throw new SQLException("NULL for required short at column "+i); return v; }
    public static double requiredDouble(ResultSet rs, int i) throws SQLException { double v=rs.getDouble(i); if(rs.wasNull())throw new SQLException("NULL for required double at column "+i); return v; }
    public static float requiredFloat(ResultSet rs, int i) throws SQLException { float v=rs.getFloat(i); if(rs.wasNull())throw new SQLException("NULL for required float at column "+i); return v; }
    public static <E extends Enum<E>> E enumValue(ResultSet rs, int i, Class<E> type) throws SQLException {
        String value=rs.getString(i); return value==null?null:Enum.valueOf(type,value);
    }
}
