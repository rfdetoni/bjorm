package com.github.rfdetoni.bjorm;

import java.util.Objects;

/** Immutable, framework-neutral pagination request.
 * Callers must resolve sort.property against a generated mapper or a static column allowlist.
 */
public record Pageable(int pageNumber,int pageSize,Sort sort) {
    public enum Direction { ASC, DESC }
    public record Sort(String property,Direction direction) {
        public Sort {
            Objects.requireNonNull(property,"sort property");
            property=property.trim();
            if(property.isEmpty()||!property.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("Sort must be a safe, single property name");
            direction=direction==null?Direction.ASC:direction;
        }
        public static Sort asc(String property) { return new Sort(property,Direction.ASC); }
        public static Sort desc(String property) { return new Sort(property,Direction.DESC); }
    }
    public Pageable {
        if(pageNumber<0)throw new IllegalArgumentException("pageNumber must be >= 0");
        if(pageSize<1||pageSize>500)throw new IllegalArgumentException("pageSize must be between 1 and 500");
        sort=sort==null?Sort.asc("id"):sort;
    }
    public static Pageable of(int pageNumber,int pageSize) {return new Pageable(pageNumber,pageSize,null);}
    public static Pageable of(int pageNumber,int pageSize,Sort sort) {return new Pageable(pageNumber,pageSize,sort);}
    public static Pageable ofSize(int pageSize) {return of(0,pageSize);}
    public long getOffset(){return (long)pageNumber*pageSize;}
    public long offsetLimited(long maxOffset) {
        if(maxOffset<0||getOffset()>maxOffset)throw new IllegalArgumentException("Offset exceeds policy; use seek pagination");
        return getOffset();
    }
    public int limitWithLookahead() {return pageSize+1;}
}
