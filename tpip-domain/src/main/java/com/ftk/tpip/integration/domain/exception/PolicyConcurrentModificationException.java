package com.ftk.tpip.integration.domain.exception;
public class PolicyConcurrentModificationException extends RuntimeException{public PolicyConcurrentModificationException(long id,long version){super("Policy "+id+" was concurrently modified; expected row version "+version);}}
