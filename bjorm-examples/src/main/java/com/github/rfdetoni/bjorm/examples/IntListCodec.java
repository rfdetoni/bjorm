package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.JsonCodec;
import java.util.*;
/** Small dependency-free numeric JSON array codec for demonstration; applications may use Jackson. */
public final class IntListCodec implements JsonCodec<List<Integer>> {
    public String encode(List<Integer> values) {
        StringJoiner out=new StringJoiner(",", "[", "]");
        for(Integer value:values)out.add(Objects.requireNonNull(value).toString());
        return out.toString();
    }
    public List<Integer> decode(String json) {
        String text=json.strip();
        if(!text.startsWith("[")||!text.endsWith("]"))throw new IllegalArgumentException("Expected JSON array");
        String inside=text.substring(1,text.length()-1).trim();
        if(inside.isEmpty())return List.of();
        return Arrays.stream(inside.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }
}
