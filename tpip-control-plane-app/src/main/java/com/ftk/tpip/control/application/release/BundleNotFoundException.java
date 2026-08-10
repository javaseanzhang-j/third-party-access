package com.ftk.tpip.control.application.release;
public class BundleNotFoundException extends RuntimeException{public BundleNotFoundException(String identity){super("Bundle does not exist: "+identity);}}
