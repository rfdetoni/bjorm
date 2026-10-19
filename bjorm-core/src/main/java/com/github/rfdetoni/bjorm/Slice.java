package com.github.rfdetoni.bjorm;

import java.util.List;
import java.util.Objects;

/** Count-free slice with a lookahead row for hasNext. */
public record Slice<T>(List<T> content,Pageable pageable,boolean hasNext) {
    public Slice {
        content=List.copyOf(Objects.requireNonNull(content,"content"));
        Objects.requireNonNull(pageable,"pageable");
        if(content.size()>pageable.pageSize())
            throw new IllegalArgumentException("Slice content exceeds pageSize");
    }
    /** Construct a slice from a limit(pageSize + 1) query result. */
    public static <T> Slice<T> fromLookahead(List<T> rows,Pageable pageable) {
        Objects.requireNonNull(rows);
        Objects.requireNonNull(pageable);
        if(rows.size()>pageable.limitWithLookahead())
            throw new IllegalArgumentException("Unexpected number of rows from a bounded slice");
        return new Slice<>(rows.subList(0,Math.min(rows.size(),pageable.pageSize())),
            pageable,rows.size()>pageable.pageSize());
    }
}
