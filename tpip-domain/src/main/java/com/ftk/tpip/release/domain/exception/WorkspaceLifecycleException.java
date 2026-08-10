package com.ftk.tpip.release.domain.exception;
public class WorkspaceLifecycleException extends RuntimeException{public WorkspaceLifecycleException(long id,String reason){super("Workspace "+id+" lifecycle conflict: "+reason);}}
