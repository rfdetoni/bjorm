package com.github.rfdetoni.bjorm.spring;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.Select;
import com.github.rfdetoni.bjorm.SqlOrder;
import com.github.rfdetoni.bjorm.SqlPredicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Spring Data paging types backed by BJORM JDBC queries; no Spring dependency in bjorm-core. */
public final class BjormPages {
    private final Bjorm db;
    private final long maxOffset;

    public BjormPages(Bjorm db) { this(db, 10_000L); }
    public BjormPages(Bjorm db, long maxOffset) {
        this.db = Objects.requireNonNull(db);
        if(maxOffset < 0) throw new IllegalArgumentException("maxOffset must be non-negative");
        this.maxOffset = maxOffset;
    }
    public long maxOffset() {return maxOffset;}
    private void checkOffset(Pageable pageable) {
        if(pageable.isPaged() && pageable.getOffset() > maxOffset)
            throw new IllegalArgumentException("OFFSET " + pageable.getOffset() + " exceeds configured bjorm.pagination.max-offset=" + maxOffset + "; use seekSlice with a cursor");
    }

    public <T> Page<T> page(Class<T> type, Pageable pageable) { return page(type, null, pageable); }

    /** Executes a SELECT and COUNT(*); use slice() to avoid counting. */
    public <T> Page<T> page(Class<T> type, SqlPredicate predicate, Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable");
        checkOffset(pageable);
        Select<T> query = select(type, predicate, pageable);
        if (pageable.isUnpaged()) throw new IllegalArgumentException("Unpaged queries are not supported; supply page and size");
        List<T> rows = query.limit(pageable.getPageSize()).offset(pageable.getOffset()).fetch();
        return new PageImpl<>(rows, pageable, db.count(type, predicate));
    }

    public <T> Slice<T> slice(Class<T> type, Pageable pageable) { return slice(type, null, pageable); }

    /** Executes exactly one SELECT LIMIT(size + 1) OFFSET; never executes COUNT(*). */
    public <T> Slice<T> slice(Class<T> type, SqlPredicate predicate, Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable");
        checkOffset(pageable);
        Select<T> query = select(type, predicate, pageable);
        if (pageable.isUnpaged()) throw new IllegalArgumentException("Unpaged queries are not supported; supply page and size");
        int size = pageable.getPageSize();
        if (size == Integer.MAX_VALUE) throw new IllegalArgumentException("Page size is too large for slice lookahead");
        List<T> rows = query.limit(size + 1).offset(pageable.getOffset()).fetch();
        boolean hasNext = rows.size() > size;
        return new SliceImpl<>(hasNext ? new ArrayList<>(rows.subList(0, size)) : rows, pageable, hasNext);
    }

    /** PostgreSQL keyset pagination by unique indexed @Id. No COUNT, OFFSET, or reflection. */
    public <T> SeekSlice<T> seekSlice(Class<T> type, Object afterId, int pageSize) {
        return seekSlice(type, null, afterId, pageSize, false);
    }
    public <T> SeekSlice<T> seekSlice(Class<T> type, SqlPredicate predicate, Object afterId,
                                    int pageSize, boolean descending) {
        if(pageSize < 1 || pageSize == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid keyset page size");
        SqlPredicate filter = predicate;
        if(afterId != null) {
            SqlPredicate seek = db.seekAfterId(type,afterId,descending);
            filter = filter == null ? seek : filter.and(seek);
        }
        Select<T> query = db.select(type).orderBy(db.primaryKeyOrder(type,descending)).limit(pageSize+1);
        if(filter != null) query.where(filter);
        List<T> rows=query.fetch();
        boolean hasNext=rows.size()>pageSize;
        List<T> content=hasNext ? new ArrayList<>(rows.subList(0,pageSize)) : rows;
        Object next=hasNext ? db.primaryKeyValue(content.getLast()) : null;
        return new SeekSlice<>(content,next,hasNext);
    }

    private <T> Select<T> select(Class<T> type, SqlPredicate predicate, Pageable pageable) {
        Objects.requireNonNull(type, "type");
        Select<T> query = db.select(type);
        if (predicate != null) query.where(predicate);
        for (Sort.Order order : pageable.getSort()) {
            if (order.isIgnoreCase() || order.getNullHandling() != Sort.NullHandling.NATIVE)
                throw new IllegalArgumentException("Unsupported sort option for property: " + order.getProperty());
            SqlOrder mapped = db.mappedOrder(type, order.getProperty(), order.isDescending());
            query.orderBy(mapped);
        }
        return query;
    }
}
