package com.ftk.tpip.release.domain.exception;
public class BundleLifecycleException extends RuntimeException{public BundleLifecycleException(long id,String reason){super("Bundle "+id+" lifecycle conflict: "+reason);}}
