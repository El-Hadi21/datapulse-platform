package com.ilias.datapulse.security;

public class SqlSecurityException extends RuntimeException {
    public SqlSecurityException(String message) {
        super(message);
    }
    public SqlSecurityException(String message, Throwable cause){
        super(message, cause);
    }
}
