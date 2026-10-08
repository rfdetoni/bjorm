package com.github.rfdetoni.bjorm;
import java.sql.*;
/** Typed JSON boundaries: one JDBC read and no global mutable codec registry. */
public final class JsonValues {
    private JsonValues(){}
    public static <T> T read(ResultSet rs,int index,JsonCodec<T> codec) throws SQLException {
        String raw=rs.getString(index);
        if(raw==null)return null;
        try{return codec.decode(raw);}catch(RuntimeException e){
            throw new SQLException("Could not decode JSON column at position "+index,e);
        }
    }
    public static <T> String write(T value,JsonCodec<T> codec) {
        return value==null?null:codec.encode(value);
    }
}
