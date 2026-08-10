package com.ftk.tpip.release.domain.exception;
public class BundleAlreadyExistsException extends RuntimeException{public BundleAlreadyExistsException(String code,String version){super("Bundle already exists: "+code+"@"+version);}}
