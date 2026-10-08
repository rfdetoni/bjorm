package com.github.rfdetoni.bjorm;
/** Default no-conversion codec for @Json String properties. */
public final class JsonStringCodec implements JsonCodec<String> {
    public String encode(String value){return value;}
    public String decode(String json){return json;}
}
