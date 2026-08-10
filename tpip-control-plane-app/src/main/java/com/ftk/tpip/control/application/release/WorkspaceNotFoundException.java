package com.ftk.tpip.control.application.release;
public class WorkspaceNotFoundException extends RuntimeException{public WorkspaceNotFoundException(long id){super("Workspace does not exist: "+id);}}
