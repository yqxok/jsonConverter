package com.yqx.jason;

public interface JsonConverter <T>{
    public T toObject(String json,T target);
    public String toJson(Object target);

}
