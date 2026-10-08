package com.github.rfdetoni.bjorm.bench;

import com.github.rfdetoni.bjorm.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Comparison of mapper-generated vs manual Java record field extraction on the same in-memory ResultSet stub. */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=3)
@Measurement(iterations=5)
@Fork(value=2)
public class JdbcMappingBenchmark {
    private static final UUID ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final EntityMapper<BenchItem> GENERATED=BenchItem_BjormMapper.INSTANCE;
    private ResultSet result;
    @Setup(Level.Trial)public void setup(){
        result=(ResultSet)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()){
            case "getObject"->ID;case "getString"->"ITEM";case "getInt"->42;
            case "wasNull"->false;
            default->throw new UnsupportedOperationException(m.getName());
        });
    }
    @Benchmark public void generated(Blackhole bh)throws SQLException{bh.consume(GENERATED.read(result));}
    @Benchmark public void manual(Blackhole bh)throws SQLException{bh.consume(new BenchItem(result.getObject(1,UUID.class),result.getString(2),JdbcValues.requiredInt(result,3)));}
}
