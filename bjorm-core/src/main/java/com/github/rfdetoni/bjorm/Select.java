package com.github.rfdetoni.bjorm;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, mutable single-use typed query builder. Explicit SQL joins; no implicit entity graph traversal. */
public final class Select<T> {
    private record Join(Class<?> type,String alias,SqlPredicate on,JoinType joinType) {}
    /** QueryDSL-style JOIN...ON builder; enforces a bound ON expression. */
    public static final class JoinOnStep<T> {
        private final Select<T> query;
        private final Class<?> type;
        private final String alias;
        private final JoinType joinType;
        private JoinOnStep(Select<T> query,Class<?> type,String alias,JoinType joinType) {
            this.query=query;this.type=Objects.requireNonNull(type);
            this.alias=identifier(alias);this.joinType=Objects.requireNonNull(joinType);
        }
        public Select<T> on(SqlPredicate predicate) {
            return query.addJoin(type,alias,Objects.requireNonNull(predicate),joinType);
        }
    }
    private static final Pattern JOIN_KEYS = Pattern.compile(
        "([A-Za-z_][A-Za-z_0-9]*)\\.[A-Za-z_][A-Za-z_0-9]*\\s*=\\s*([A-Za-z_][A-Za-z_0-9]*)\\.[A-Za-z_][A-Za-z_0-9]*");
    private final Operations db;
    private final Class<T> type;
    private SqlPredicate predicate;
    private String alias;
    private final List<Join> joins=new ArrayList<>();
    private final List<SqlOrder> orders=new ArrayList<>();
    private Integer limit;
    private Long offset;
    Select(Operations db,Class<T> type){this.db=Objects.requireNonNull(db);this.type=Objects.requireNonNull(type);}
    public Select<T> as(String value){this.alias=identifier(value);return this;}
    public JoinOnStep<T> join(Class<?> type,String alias) {return new JoinOnStep<>(this,type,alias,JoinType.INNER);}
    public JoinOnStep<T> innerJoin(Class<?> type,String alias) {return join(type,alias);}
    public JoinOnStep<T> leftJoin(Class<?> type,String alias) {return new JoinOnStep<>(this,type,alias,JoinType.LEFT);}
    public JoinOnStep<T> rightJoin(Class<?> type,String alias) {return new JoinOnStep<>(this,type,alias,JoinType.RIGHT);}
    public JoinOnStep<T> fullJoin(Class<?> type,String alias) {return new JoinOnStep<>(this,type,alias,JoinType.FULL);}
    public JoinOnStep<T> outerJoin(Class<?> type,String alias) {return fullJoin(type,alias);}
    public JoinOnStep<T> fullOuterJoin(Class<?> type,String alias) {return fullJoin(type,alias);}
    public Select<T> join(Class<?> type,String alias,SqlPredicate on) {return join(type,alias).on(on);}
    public Select<T> innerJoin(Class<?> type,String alias,SqlPredicate on) {return join(type,alias).on(on);}
    public Select<T> leftJoin(Class<?> type,String alias,SqlPredicate on) {return leftJoin(type,alias).on(on);}
    public Select<T> rightJoin(Class<?> type,String alias,SqlPredicate on) {return rightJoin(type,alias).on(on);}
    public Select<T> fullJoin(Class<?> type,String alias,SqlPredicate on) {return fullJoin(type,alias).on(on);}
    public Select<T> outerJoin(Class<?> type,String alias,SqlPredicate on) {return fullJoin(type,alias).on(on);}
    public Select<T> fullOuterJoin(Class<?> type,String alias,SqlPredicate on) {return fullJoin(type,alias).on(on);}
    private Select<T> addJoin(Class<?> type,String alias,SqlPredicate on,JoinType joinType){
        joins.add(new Join(Objects.requireNonNull(type),identifier(alias),Objects.requireNonNull(on),joinType));return this;
    }
    private static String identifier(String value){
        if(value==null||!value.matches("[A-Za-z_][A-Za-z_0-9]*"))throw new IllegalArgumentException("Invalid SQL alias: "+value);
        return value;
    }
    public Select<T> where(SqlPredicate value){predicate=Objects.requireNonNull(value);return this;}
    Select<T> whereNullable(SqlPredicate value){predicate=value;return this;}
    public Select<T> orderBy(SqlOrder... values){orders.addAll(Arrays.asList(values));return this;}
    public Select<T> limit(int value){if(value<1)throw new IllegalArgumentException("limit must be positive");limit=value;return this;}
    public Select<T> offset(long value){if(value<0)throw new IllegalArgumentException("offset must be non-negative");offset=value;return this;}
    public List<T> fetch(){return db.list(this);}
    /** Restrict SELECT to named Java properties. Does not materialize a partial entity. */
    public FieldSelect<T> fields(String... properties){return new FieldSelect<>(this, properties);}
    /** Fetch one mapped entity, using SQL LIMIT 1. */
    public Optional<T> first(){limit(1);return db.first(this);}
    Operations operations(){return db;}
    boolean hasJoins(){return !joins.isEmpty();}
    boolean hasRightOrFullJoin(){return joins.stream().anyMatch(j -> j.joinType()==JoinType.RIGHT || j.joinType()==JoinType.FULL);}
    List<SqlOrder> sorting(){return List.copyOf(orders);}


    Class<T> type(){return type;}
    String sql(EntityMapper<T> base,Function<Class<?>,EntityMapper<?>> lookup){
        return sql(base, lookup, null, SqlDialects.POSTGRESQL);
    }
    String sql(EntityMapper<T> base,Function<Class<?>,EntityMapper<?>> lookup,SqlDialect dialect){
        return sql(base,lookup,null,dialect);
    }
    String sql(EntityMapper<T> base,Function<Class<?>,EntityMapper<?>> lookup,String[] properties){
        return sql(base,lookup,properties,SqlDialects.POSTGRESQL);
    }
    String sql(EntityMapper<T> base,Function<Class<?>,EntityMapper<?>> lookup,String[] properties,SqlDialect dialect){
        if(properties!=null && properties.length==0)throw new IllegalArgumentException("Select at least one property");
        if(limit!=null && !joins.isEmpty())
            throw new IllegalArgumentException("Paginating JOIN results may multiply root entities; page root IDs first, then fetch relations separately");
        StringBuilder sql=new StringBuilder();
        if(properties==null && alias==null && joins.isEmpty()){sql.append(base.selectAllSql());}
        else {
            boolean qualified=alias!=null||!joins.isEmpty();
            String root=alias==null?"a":alias;
            sql.append("SELECT ");
            if(properties==null)sql.append(base.qualifiedColumns(root));
            else for(int n=0;n<properties.length;n++) {
                if(n>0)sql.append(", ");
                if(qualified)sql.append(root).append('.');
                sql.append(base.columnFor(properties[n]));
            }
            sql.append(" FROM ").append(base.table());
            if(qualified)sql.append(" ").append(root);
            Set<String> known=new HashSet<>();known.add(root);
            for(Join join:joins){
                if(!known.add(join.alias))throw new IllegalArgumentException("Duplicate SQL alias: "+join.alias);
                // OR clauses can make an otherwise linked join degenerate into a Cartesian product.
                if(Pattern.compile("(?i)\\bOR\\b").matcher(join.on.sql()).find())
                    throw new IllegalArgumentException("JOIN ON with OR is not supported; it can multiply rows unpredictably");
                boolean linked=false;
                Matcher matcher=JOIN_KEYS.matcher(join.on.sql());
                while(matcher.find()) {
                    String a=matcher.group(1),b=matcher.group(2);
                    if(!a.equals(b) && (a.equals(join.alias) && known.contains(b) || b.equals(join.alias) && known.contains(a))) {
                        linked=true; break;
                    }
                }
                if(!linked) throw new IllegalArgumentException("JOIN ON must relate indexed/mapped columns of two distinct table aliases; cartesian joins are not supported");
                EntityMapper<?> target=lookup.apply(join.type);
                sql.append(" ").append(dialect.join(join.joinType)).append(" ").append(target.table()).append(" ").append(join.alias)
                        .append(" ON ").append(join.on.sql());
            }
        }
        if(predicate!=null)sql.append(" WHERE ").append(predicate.sql());
        if(!orders.isEmpty()){
            sql.append(" ORDER BY ");
            for(int i=0;i<orders.size();i++){if(i>0)sql.append(", ");sql.append(orders.get(i).sql());}
        }
        if(limit!=null)sql.append(" LIMIT ?");
        if(offset!=null){if(limit==null)throw new IllegalStateException("offset requires limit");sql.append(" OFFSET ?");}
        return sql.toString();
    }
    int bind(PreparedStatement ps) throws SQLException { return bind(ps,1,SqlDialects.POSTGRESQL); }
    int bind(PreparedStatement ps,int start) throws SQLException { return bind(ps,start,SqlDialects.POSTGRESQL); }
    int bind(PreparedStatement ps,SqlDialect dialect) throws SQLException {return bind(ps,1,dialect);}
    int bind(PreparedStatement ps,int start,SqlDialect dialect) throws SQLException {
        int i=start;
        for(Join join:joins)for(Object value:join.on.params())dialect.bindValue(ps,i++,value);
        if(predicate!=null)for(Object value:predicate.params())ps.setObject(i++,value);
        if(limit!=null)ps.setInt(i++,limit);
        if(offset!=null)ps.setLong(i++,offset);
        return i;
    }
}
