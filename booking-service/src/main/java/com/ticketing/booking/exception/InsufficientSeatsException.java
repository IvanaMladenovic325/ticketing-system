package com.ticketing.booking.exception;

public class InsufficientSeatsException extends RuntimeException {

    public InsufficientSeatsException(Long eventId, int requested, int available) {
        super("Event with id " + eventId + " has " + available + " available seats, requested " + requested);
    }

}
