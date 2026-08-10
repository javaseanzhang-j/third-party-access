package com.ftk.tpip.integration.domain.exception;
public class PolicyCodeAlreadyExistsException extends RuntimeException{public PolicyCodeAlreadyExistsException(String code){super("Policy code already exists: "+code);}}
