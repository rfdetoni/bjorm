package com.github.rfdetoni.bjorm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, fully materialized JDBC call outcome. No ResultSet escapes its connection. */
public final class RoutineResult {
    private final Map<String, Object> outputs;
    private final List<List<?>> resultSets;
    private final List<Integer> updateCounts;

    RoutineResult(Map<String, Object> outputs, List<List<?>> resultSets, List<Integer> updateCounts) {
        // OUT parameters can be SQL NULL; Map.copyOf would incorrectly reject them.
        this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        List<List<?>> immutable = new ArrayList<>(resultSets.size());
        for (List<?> rows : resultSets) immutable.add(List.copyOf(rows));
        this.resultSets = List.copyOf(immutable);
        this.updateCounts = List.copyOf(updateCounts);
    }

    public Map<String, Object> outputs() { return outputs; }
    public List<Integer> updateCounts() { return updateCounts; }
    public int resultSetCount() { return resultSets.size(); }

    public <T> T get(String name, Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (!outputs.containsKey(name)) throw new IllegalArgumentException("Unknown OUT parameter: " + name);
        Object value = outputs.get(name);
        return value == null ? null : type.cast(value);
    }

    public <T> List<T> rows(int index, Class<T> type) {
        Objects.requireNonNull(type, "type");
        List<?> rows = resultSets.get(index);
        return rows.stream().map(type::cast).toList();
    }
}
