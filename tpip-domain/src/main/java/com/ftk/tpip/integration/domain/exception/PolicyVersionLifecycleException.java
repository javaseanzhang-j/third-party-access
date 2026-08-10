package com.ftk.tpip.integration.domain.exception;
public class PolicyVersionLifecycleException extends RuntimeException{public PolicyVersionLifecycleException(long id,String reason){super("Policy version "+id+" lifecycle conflict: "+reason);}}
