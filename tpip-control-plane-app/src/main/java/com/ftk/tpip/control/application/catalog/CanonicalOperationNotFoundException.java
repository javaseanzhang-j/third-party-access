package com.ftk.tpip.control.application.catalog;
public class CanonicalOperationNotFoundException extends RuntimeException {
    public CanonicalOperationNotFoundException(long id){super("Canonical operation not found: "+id);}
}
