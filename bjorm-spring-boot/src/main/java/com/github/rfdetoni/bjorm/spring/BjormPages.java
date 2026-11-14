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
import java.util.HashSet;
import java.util.Set;

/** Spring Data paging types backed by BJORM JDBC queries; no Spring dependency in bjorm-core. */
public final class BjormPages {
    private final Bjorm db;
    private final long maxOffset;
    private final int maxPageSize;

    public BjormPages(Bjorm db) { this(db, 10_000L, 500); }
    public BjormPages(Bjorm db, long maxOffset) { this(db, maxOffset, 500); }
    public BjormPages(Bjorm db, long maxOffset, int maxPageSize) {
        this.db = Objects.requireNonNull(db);
        if(maxOffset < 0) throw new IllegalArgumentException("maxOffset must be non-negative");
        if(maxPageSize < 1 || maxPageSize == Integer.MAX_VALUE)
            throw new IllegalArgumentException("maxPageSize must be between 1 and Integer.MAX_VALUE - 1");
        this.maxOffset = maxOffset;
        this.maxPageSize = maxPageSize;
    }
    public long maxOffset() {return maxOffset;}
    public int maxPageSize() {return maxPageSize;}
    private void checkOffset(Pageable pageable) {
        if(pageable.isPaged() && pageable.getOffset() > maxOffset)
            throw new IllegalArgumentException("OFFSET " + pageable.getOffset() + " exceeds configured bjorm.pagination.max-offset=" + maxOffset + "; use seekSlice with a cursor");
        if(pageable.isPaged())checkPageSize(pageable.getPageSize());
    }
    private void checkPageSize(int requested) {
        if(requested < 1 || requested > maxPageSize)
            throw new IllegalArgumentException("Page size " + requested + " exceeds configured bjorm.pagination.max-page-size=" + maxPageSize);
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
        checkPageSize(pageSize);
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

    /**
     * Stable keyset pagination ordered by an indexed scalar plus the primary key.
     * Uses a single SELECT with LIMIT(size+1), no OFFSET or COUNT. The sort
     * column must be non-null; composite primary keys need an explicit tuple plan.
     */
    public <T> SeekSlice<T> seekSliceSorted(Class<T> type,String sortProperty,
                                            SortedCursor after,int pageSize) {
        return seekSliceSorted(type,sortProperty,null,after,pageSize,false);
    }

    public <T> SeekSlice<T> seekSliceSorted(Class<T> type,String sortProperty,
                                            SqlPredicate predicate,SortedCursor after,
                                            int pageSize,boolean descending) {
        checkPageSize(pageSize);
        Objects.requireNonNull(type,"type");
        Objects.requireNonNull(sortProperty,"sortProperty");
        if(db.primaryKeyProperties(type).contains(sortProperty))
            throw new IllegalArgumentException("Use seekSlice for primary-key sorting");
        SqlOrder sorted=db.mappedOrder(type,sortProperty,descending);
        SqlOrder key=db.primaryKeyOrder(type,descending);
        SqlPredicate filter=predicate;
        if(after!=null) {
            SqlPredicate seek=db.seekAfterSorted(type,sortProperty,
                after.sortValue(),after.id(),descending);
            filter=filter==null?seek:filter.and(seek);
        }
        Select<T> query=db.select(type).orderBy(sorted,key).limit(pageSize+1);
        if(filter!=null)query.where(filter);
        List<T> rows=query.fetch();
        boolean hasNext=rows.size()>pageSize;
        List<T> content=hasNext?new ArrayList<>(rows.subList(0,pageSize)):rows;
        SortedCursor next=hasNext?new SortedCursor(
            db.mappedPropertyValue(content.getLast(),sortProperty),
            db.primaryKeyValue(content.getLast())):null;
        return new SeekSlice<>(content,next,hasNext);
    }

    private <T> Select<T> select(Class<T> type, SqlPredicate predicate, Pageable pageable) {
        Objects.requireNonNull(type, "type");
        Select<T> query = db.select(type);
        if (predicate != null) query.where(predicate);
        Set<String> sorted = new HashSet<>();
        boolean descending = false;
        for (Sort.Order order : pageable.getSort()) {
            if (order.isIgnoreCase() || order.getNullHandling() != Sort.NullHandling.NATIVE)
                throw new IllegalArgumentException("Unsupported sort option for property: " + order.getProperty());
            SqlOrder mapped = db.mappedOrder(type, order.getProperty(), order.isDescending());
            query.orderBy(mapped);
            sorted.add(order.getProperty());
            descending = order.isDescending();
        }
        // Every offset page needs a total order. A non-unique name/createdAt sort
        // alone can skip or duplicate rows even without concurrent writes.
        for (String key : db.primaryKeyProperties(type)) {
            if (!sorted.contains(key)) query.orderBy(db.mappedOrder(type, key, descending));
        }
        return query;
    }
}
