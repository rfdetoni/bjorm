package com.github.rfdetoni.bjorm;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Reads ordinal enums with range checks, no runtime entity field reflection. */
public final class EnumValues {
    private EnumValues() {}
    public static <E extends Enum<E>> E read(ResultSet rs,int index,Class<E> type)throws SQLException {
        Object raw=rs.getObject(index);
        if(raw==null)return null;
        if(!(raw instanceof Number number))
            throw new SQLException("Expected numeric enum ordinal at column "+index);
        int ordinal=number.intValue();
        if(number.doubleValue()!=ordinal)
            throw new SQLException("Fractional enum ordinal at column "+index);
        E[] values=type.getEnumConstants();
        if(ordinal<0 || ordinal>=values.length)
            throw new SQLException("Enum ordinal out of range for "+type.getName()+" at column "+index);
        return values[ordinal];
    }
}
