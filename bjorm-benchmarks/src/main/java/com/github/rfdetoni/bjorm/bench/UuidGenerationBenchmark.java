package com.github.rfdetoni.bjorm.bench;

import com.github.rfdetoni.bjorm.UuidV7;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Throughput comparison: no JMH result should be presented as a latency SLA. */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations=3)
@Measurement(iterations=5)
@Fork(2)
public class UuidGenerationBenchmark {
    @Benchmark public void uuidV7(Blackhole blackhole) {
        blackhole.consume(UuidV7.next());
    }
    @Benchmark public void randomV4(Blackhole blackhole) {
        blackhole.consume(UUID.randomUUID());
    }
}
