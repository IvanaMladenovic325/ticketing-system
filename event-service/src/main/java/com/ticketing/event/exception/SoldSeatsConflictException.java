package com.ticketing.event.exception;

public class SoldSeatsConflictException extends RuntimeException {

    public SoldSeatsConflictException(String message) {
        super(message);
    }

}
