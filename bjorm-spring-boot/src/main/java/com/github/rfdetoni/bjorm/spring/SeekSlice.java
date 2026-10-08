package com.github.rfdetoni.bjorm.spring;

import java.util.List;

/** Cursor-based page: nextCursor is null when there is no next page; never uses OFFSET or COUNT. */
public record SeekSlice<T>(List<T> content, Object nextCursor, boolean hasNext) {
    public SeekSlice { content=List.copyOf(content); }
}
