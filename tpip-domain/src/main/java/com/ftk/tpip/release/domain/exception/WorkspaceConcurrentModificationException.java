package com.ftk.tpip.release.domain.exception;
public class WorkspaceConcurrentModificationException extends RuntimeException{public WorkspaceConcurrentModificationException(long id,long version){super("Workspace "+id+" was concurrently modified; expected row version "+version);}}
