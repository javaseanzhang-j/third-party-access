package com.ftk.tpip.release.domain.exception;
public class WorkspaceAlreadyExistsException extends RuntimeException{public WorkspaceAlreadyExistsException(String code){super("Workspace code already exists: "+code);}}
