package com.ftk.tpip.runtime.access;
public final class ConsumerAuthorizationException extends RuntimeException {
    private final String code;
    public ConsumerAuthorizationException(String code,String message){super(message);this.code=code;}
    public String code(){return code;}
}
