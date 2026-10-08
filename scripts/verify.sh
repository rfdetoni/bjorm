#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/core" "$OUT/processor" "$OUT/example" "$OUT/generated"
javac -d "$OUT/core" $(find bjorm-core/src/main/java -name '*.java')
javac -cp "$OUT/core" -d "$OUT/processor" $(find bjorm-processor/src/main/java -name '*.java')
javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor dev.bjorm.processor.EntityProcessor -s "$OUT/generated" -d "$OUT/example" $(find bjorm-examples/src/main/java -name '*.java')
javac -cp "$OUT/core:$OUT/example" -d "$OUT/example" $(find bjorm-examples/src/test/java -name '*.java')
java -cp "$OUT/core:$OUT/example" dev.bjorm.examples.SmokeTest

java -cp "$OUT/core:$OUT/example" dev.bjorm.examples.AdvancedSmokeTest

java -cp "$OUT/core:$OUT/example${BJORM_DRIVER_JAR:+:$BJORM_DRIVER_JAR}" dev.bjorm.examples.PostgresIntegrationTest

# The processor must reject unsafe identifiers and unbound query parameters at compile time.
cat > "$OUT/InvalidTable.java" <<'JAVA'
import dev.bjorm.*;
@Table("users;DROP") public record InvalidTable(@Id int id, String name) {}
JAVA
if javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor dev.bjorm.processor.EntityProcessor -d "$OUT/example" "$OUT/InvalidTable.java" 2>"$OUT/negative.log"; then
  echo 'FAIL: processor accepted unsafe SQL table name' >&2; exit 1
fi
grep -q 'Invalid table identifier' "$OUT/negative.log"
cat > "$OUT/InvalidQuery.java" <<'JAVA'
import dev.bjorm.*;
public interface InvalidQuery { @Query("SELECT * FROM users WHERE id=:unknown") int run(@Param("id") int id); }
JAVA
if javac -cp "$OUT/core" -processorpath "$OUT/core:$OUT/processor" -processor dev.bjorm.processor.EntityProcessor -d "$OUT/example" "$OUT/InvalidQuery.java" 2>"$OUT/negative.log"; then
  echo 'FAIL: processor accepted an unbound query parameter' >&2; exit 1
fi
grep -q 'Unknown SQL parameter' "$OUT/negative.log"
if grep -R -l 'import org\.springframework' bjorm-core/src/main/java bjorm-processor/src/main/java; then
  echo 'FAIL: framework dependency leaked into BJORM core/processor' >&2; exit 1
fi
echo 'PASS: compile-time reject unsafe identifier/unknown named parameter; core/processor remain framework-free'
