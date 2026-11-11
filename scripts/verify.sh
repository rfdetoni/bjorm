#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/core" "$OUT/processor" "$OUT/example" "$OUT/generated"
javac -d "$OUT/core" $(find bjorm-core/src/main/java -name '*.java')
javac -cp "$OUT/core" -d "$OUT/processor" $(find bjorm-processor/src/main/java -name '*.java')
javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor com.github.rfdetoni.bjorm.processor.EntityProcessor -s "$OUT/generated" -d "$OUT/example" $(find bjorm-examples/src/main/java -name '*.java')
javac -cp "$OUT/core:$OUT/example" -d "$OUT/example" $(find bjorm-examples/src/test/java -name '*.java')
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.SmokeTest

java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.AdvancedSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.TenantSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.MappingFlagsSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.OrdinalSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.AuditSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.IdStrategySmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.UuidV7ConcurrencySmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.StaticSchemaSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.GraphQuerySmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.ManyToOneSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.MixedGraphSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.EmbeddedSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.CompositeIdSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.GraphSharedRelationSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.AnnotatedJoinSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.DialectSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.InheritedFieldSmokeTest
java -cp "$OUT/core:$OUT/example" com.github.rfdetoni.bjorm.examples.TypedJsonSmokeTest
java -cp "$OUT/core:$OUT/example${BJORM_MYSQL_DRIVER_JAR:+:$BJORM_MYSQL_DRIVER_JAR}" com.github.rfdetoni.bjorm.examples.MySqlIntegrationTest
java -cp "$OUT/core:$OUT/example${BJORM_MYSQL_DRIVER_JAR:+:$BJORM_MYSQL_DRIVER_JAR}" com.github.rfdetoni.bjorm.examples.MySqlRoleIsolationTest

java -cp "$OUT/core:$OUT/example${BJORM_DRIVER_JAR:+:$BJORM_DRIVER_JAR}" com.github.rfdetoni.bjorm.examples.PostgresIntegrationTest
java -cp "$OUT/core:$OUT/example${BJORM_DRIVER_JAR:+:$BJORM_DRIVER_JAR}" com.github.rfdetoni.bjorm.examples.PostgresRoleIsolationTest


# Cross-module inheritance: base annotations must survive compilation into a separate JAR/module.
mkdir -p "$OUT/shared-base" "$OUT/consumer"
javac -cp "$OUT/core" -d "$OUT/shared-base" bjorm-examples/src/main/java/com/github/rfdetoni/bjorm/examples/AuditedBase.java
javac -cp "$OUT/core:$OUT/shared-base" \
  -processorpath "$OUT/core:$OUT/processor" \
  -processor com.github.rfdetoni.bjorm.processor.EntityProcessor \
  -d "$OUT/consumer" \
  bjorm-examples/src/main/java/com/github/rfdetoni/bjorm/examples/InheritedPatient.java \
  bjorm-examples/src/main/java/com/github/rfdetoni/bjorm/examples/ItInheritedFkChild.java \
  bjorm-examples/src/main/java/com/github/rfdetoni/bjorm/examples/ItInheritedFkParent.java \
  bjorm-examples/src/test/java/com/github/rfdetoni/bjorm/examples/InheritedFieldSmokeTest.java
java -cp "$OUT/core:$OUT/shared-base:$OUT/consumer" com.github.rfdetoni.bjorm.examples.InheritedFieldSmokeTest
echo 'PASS: annotations retained when base is compiled in another module'

# The processor must reject unsafe identifiers and unbound query parameters at compile time.
cat > "$OUT/InvalidTable.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
@Table("users;DROP") public record InvalidTable(@Id int id, String name) {}
JAVA
if javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor com.github.rfdetoni.bjorm.processor.EntityProcessor -d "$OUT/example" "$OUT/InvalidTable.java" 2>"$OUT/negative.log"; then
  echo 'FAIL: processor accepted unsafe SQL table name' >&2; exit 1
fi
grep -q 'Invalid table identifier' "$OUT/negative.log"
cat > "$OUT/InvalidQuery.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
public interface InvalidQuery { @Query("SELECT * FROM users WHERE id=:unknown") int run(@Param("id") int id); }
JAVA
if javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor com.github.rfdetoni.bjorm.processor.EntityProcessor -d "$OUT/example" "$OUT/InvalidQuery.java" 2>"$OUT/negative.log"; then
  echo 'FAIL: processor accepted an unbound query parameter' >&2; exit 1
fi
grep -q 'Unknown SQL parameter' "$OUT/negative.log"
# Explicit ID strategies are compile-time contracts, not runtime defaults.
cat > "$OUT/InvalidIdentityId.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("invalid_identity_id")
public record InvalidIdentityId(@Id(strategy=IdStrategy.IDENTITY) UUID id,String name) {}
JAVA
if javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor com.github.rfdetoni.bjorm.processor.EntityProcessor -d "$OUT/example" "$OUT/InvalidIdentityId.java" 2>"$OUT/id-negative.log"; then
  echo 'FAIL: processor accepted database identity for UUID' >&2; exit 1
fi
grep -q 'requires int/long/Integer/Long' "$OUT/id-negative.log"
cat > "$OUT/InvalidMixedStrategy.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("invalid_mixed_id")
public record InvalidMixedStrategy(@Id(strategy=IdStrategy.ASSIGNED,uuidV7=true) UUID id,String name) {}
JAVA
if javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor com.github.rfdetoni.bjorm.processor.EntityProcessor -d "$OUT/example" "$OUT/InvalidMixedStrategy.java" 2>"$OUT/id-negative.log"; then
  echo 'FAIL: processor accepted conflicting @Id policy flags' >&2; exit 1
fi
grep -q 'cannot be combined with legacy' "$OUT/id-negative.log"
cat > "$OUT/UnsafeSchema.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table(value="safe_table",schema="bad;DROP TABLE users")
public record UnsafeSchema(@Id UUID id,String name) {}
JAVA
if javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor com.github.rfdetoni.bjorm.processor.EntityProcessor -d "$OUT/example" "$OUT/UnsafeSchema.java" 2>"$OUT/schema-negative.log"; then
  echo 'FAIL: processor accepted unsafe static schema identifier' >&2; exit 1
fi
grep -q 'Invalid static schema identifier' "$OUT/schema-negative.log"
# Invalid @ManyToOne foreign key must be rejected by the annotation processor.
cat > "$OUT/InvalidToOne.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
import com.github.rfdetoni.bjorm.examples.ItUser;
import java.util.UUID;
@Table("invalid_to_one")
public record InvalidToOne(@Id UUID id,Long customerId,String label,
    @ManyToOne(mappedBy="customerId") ItUser customer) {}
JAVA
if javac -cp "$OUT/core:$OUT/example" -processorpath "$OUT/core:$OUT/processor" \
  -processor com.github.rfdetoni.bjorm.processor.EntityProcessor \
  -d "$OUT/example" "$OUT/InvalidToOne.java" 2>"$OUT/toone-negative.log"; then
  echo 'FAIL: processor accepted an incompatible @ManyToOne foreign key' >&2; exit 1
fi
if ! grep -q '@ManyToOne foreign key must match target @Id type' "$OUT/toone-negative.log"; then
  echo 'Unexpected @ManyToOne compile diagnostic:' >&2
  cat "$OUT/toone-negative.log" >&2
  exit 1
fi
cat > "$OUT/InvalidEmbedded.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("invalid_embedded")
public record InvalidEmbedded(@Id UUID id, String label, @Embedded String address) {}
JAVA
if javac -cp "$OUT/core:$OUT/example" -processorpath "$OUT/core:$OUT/processor" \
  -processor com.github.rfdetoni.bjorm.processor.EntityProcessor \
  -d "$OUT/example" "$OUT/InvalidEmbedded.java" 2>"$OUT/embedded-negative.log"; then
  echo 'FAIL: annotation processor accepted non-record embedded value' >&2; exit 1
fi
if ! grep -q '@Embedded requires a public record value type' "$OUT/embedded-negative.log"; then
  cat "$OUT/embedded-negative.log" >&2; exit 1
fi
# Multi-part primary keys must opt in explicitly; inferred UUID IDs are rejected.
cat > "$OUT/InvalidCompositeId.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("invalid_composite_ids")
public record InvalidCompositeId(
    @Id UUID tenantId,
    @Id(strategy=IdStrategy.ASSIGNED) String productId,
    String name) {}
JAVA
if javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" \
  -processor com.github.rfdetoni.bjorm.processor.EntityProcessor \
  -d "$OUT/example" "$OUT/InvalidCompositeId.java" 2>"$OUT/composite-negative.log"; then
  echo 'FAIL: inferred and assigned composite keys were mixed' >&2; exit 1
fi
if ! grep -q 'Composite @Id components require explicit strategy=ASSIGNED' "$OUT/composite-negative.log"; then
  cat "$OUT/composite-negative.log" >&2; exit 1
fi
# Reject mismatched value-object converter generics at Java compilation time.
cat > "$OUT/InvalidConverted.java" <<'JAVA'
import com.github.rfdetoni.bjorm.*;
import com.github.rfdetoni.bjorm.examples.MoneyValueConverter;
import java.util.UUID;
@Table("invalid_converted")
public record InvalidConverted(@Id UUID id,
    @Converted(MoneyValueConverter.class) String amount) {}
JAVA
if javac -cp "$OUT/core:$OUT/example" -processorpath "$OUT/core:$OUT/processor" \
  -processor com.github.rfdetoni.bjorm.processor.EntityProcessor \
  -d "$OUT/example" "$OUT/InvalidConverted.java" 2>"$OUT/converted-negative.log"; then
  echo 'FAIL: processor accepted a mismatched JDBC converter generic type' >&2; exit 1
fi
if ! grep -q 'incompatible types' "$OUT/converted-negative.log"; then
  echo 'Unexpected converted-column compilation diagnostic:' >&2
  cat "$OUT/converted-negative.log" >&2
  exit 1
fi
if grep -R -l 'import org\.springframework' bjorm-core/src/main/java bjorm-processor/src/main/java; then
  echo 'FAIL: framework dependency leaked into BJORM core/processor' >&2; exit 1
fi
echo 'PASS: compile-time reject unsafe identifier/unknown named parameter; core/processor remain framework-free'
# Verify SPI descriptors point to loadable classes after Java-package migration.
grep -qx 'com.github.rfdetoni.bjorm.processor.EntityProcessor' bjorm-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor
grep -qx 'com.github.rfdetoni.bjorm.spring.BjormAutoConfiguration' bjorm-spring-boot/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
if grep -Rq 'dev\.bjorm' bjorm-core/src bjorm-processor/src bjorm-spring-boot/src bjorm-examples/src bjorm-benchmarks/src 2>/dev/null; then
  echo 'FAIL: stale dev.bjorm reference after namespace migration' >&2; exit 1
fi
echo 'PASS: processor and Spring SPI descriptors use the new namespace'

