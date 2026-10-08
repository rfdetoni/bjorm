package com.github.rfdetoni.bjorm;
/** User-owned JSON serializer; BJORM stays independent of JSON library/framework. */
public interface JsonCodec<T> {
    String encode(T value);
    T decode(String json);
}
