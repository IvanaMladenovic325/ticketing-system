package com.ticketing.booking.exception;

import com.ticketing.booking.model.BookingStatus;

public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(Long id, BookingStatus status, String action) {
        super("Booking with id " + id + " in status " + status + " cannot be " + action);
    }

}
