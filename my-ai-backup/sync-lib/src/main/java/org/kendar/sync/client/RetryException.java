package org.kendar.sync.client;

public class RetryException extends RuntimeException{
    private final String code;
    private final String details;

    public RetryException(String code, String message, String details) {
        super("Code: " + code + ", Message: " + message + ", Details: " + details);
        this.code = code;
        this.details = details;
    }

    public String getCode() {
        return code;
    }


    public String getDetails() {
        return details;
    }
}
