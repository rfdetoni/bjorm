package com.github.rfdetoni.bjorm;

import java.util.*;

/** Selected Java properties with either a lightweight row-map or a compiled DTO reader. */
public final class FieldSelect<T> {
    private final Select<T> query;
    private final String[] properties;

    FieldSelect(Select<T> query, String[] properties) {
        this.query = Objects.requireNonNull(query);
        Objects.requireNonNull(properties);
        if (properties.length == 0) throw new IllegalArgumentException("Select at least one property");
        this.properties = properties.clone();
        Set<String> names = new HashSet<>();
        for (String name : this.properties)
            if (name == null || !names.add(name))
                throw new IllegalArgumentException("Null or duplicate property: " + name);
    }

    public FieldSelect<T> where(SqlPredicate predicate){query.where(predicate);return this;}
    public FieldSelect<T> orderBy(SqlOrder... orders){query.orderBy(orders);return this;}
    public FieldSelect<T> limit(int size){query.limit(size);return this;}
    public FieldSelect<T> offset(int offset){query.offset(offset);return this;}
    public FieldSelect<T> as(String alias){query.as(alias);return this;}
    public FieldSelect<T> join(Class<?> type,String alias,SqlPredicate on){query.join(type,alias,on);return this;}
    public FieldSelect<T> leftJoin(Class<?> type,String alias,SqlPredicate on){query.leftJoin(type,alias,on);return this;}

    public List<Map<String,Object>> fetch(){return query.operations().fieldRows(query,properties);}
    public Optional<Map<String,Object>> first(){query.limit(1);return query.operations().fieldOne(query,properties);}
    /** Fast scalar path: one generated property reader, no intermediary row Map. */
    public <C> List<C> values(Class<C> type) {return query.operations().columnValues(query,singleProperty(),type);}
    public <C> Optional<C> firstValue(Class<C> type) {return query.operations().columnOne(query,singleProperty(),type);}
    private String singleProperty(){
        if(properties.length!=1)throw new IllegalStateException("Scalar query must select exactly one property");
        return properties[0];
    }
    /** The generated @Projection mapper reads only the requested columns, in their declared order. */
    public <P> List<P> fetch(RowMapper<P> projection){return query.operations().project(query,properties,projection);}
    public <P> Optional<P> first(RowMapper<P> projection){query.limit(1);return query.operations().projectOne(query,properties,projection);}
}
