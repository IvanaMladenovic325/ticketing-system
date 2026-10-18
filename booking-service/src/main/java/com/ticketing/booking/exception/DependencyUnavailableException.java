package com.ticketing.booking.exception;

public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String serviceName, Throwable cause) {
        super("Service " + serviceName + " is currently unavailable", cause);
    }

}
