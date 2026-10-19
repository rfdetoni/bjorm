package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.Pageable;
import com.github.rfdetoni.bjorm.Slice;
import java.util.List;
public final class PagingSmokeTest {
    private static void expect(boolean condition) {if(!condition)throw new AssertionError();}
    private static void rejected(Runnable work) {
        try {work.run();throw new AssertionError("Expected rejection");}
        catch(IllegalArgumentException expected) {}
    }
    public static void main(String[] args) {
        Pageable p=Pageable.of(2,3,Pageable.Sort.desc("createdAt"));
        expect(p.getOffset()==6);
        expect(p.limitWithLookahead()==4);
        rejected(()->new Pageable(-1,5,null));
        rejected(()->new Pageable(1,0,null));
        rejected(()->new Pageable(0,501,null));
        rejected(()->Pageable.Sort.asc("id;DROP TABLE"));
        rejected(()->p.offsetLimited(5));
        Slice<Integer> s=Slice.fromLookahead(List.of(1,2,3,4),p);
        expect(s.hasNext()&&s.content().equals(List.of(1,2,3)));
        rejected(()->Slice.fromLookahead(List.of(1,2,3,4,5),p));
        System.out.println("BJORM paging smoke passed");
    }
}
