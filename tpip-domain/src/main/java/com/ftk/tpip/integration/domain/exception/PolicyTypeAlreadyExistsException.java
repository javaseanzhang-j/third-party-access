package com.ftk.tpip.integration.domain.exception;
public class PolicyTypeAlreadyExistsException extends RuntimeException{public PolicyTypeAlreadyExistsException(String ref){super("Policy type already exists: "+ref);}}
