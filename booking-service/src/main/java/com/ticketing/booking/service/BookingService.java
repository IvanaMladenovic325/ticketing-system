package com.ticketing.booking.service;

import com.ticketing.booking.client.EventClient;
import com.ticketing.booking.client.EventResponse;
import com.ticketing.booking.client.PaymentGateway;
import com.ticketing.booking.client.PaymentOutcome;
import com.ticketing.booking.client.UserClient;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.dto.CreateBookingRequest;
import com.ticketing.booking.exception.BookingNotFoundException;
import com.ticketing.booking.exception.DependencyUnavailableException;
import com.ticketing.booking.exception.EventNotFoundException;
import com.ticketing.booking.exception.InsufficientSeatsException;
import com.ticketing.booking.exception.InvalidBookingStateException;
import com.ticketing.booking.exception.UserNotFoundException;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingStatus;
import com.ticketing.booking.repository.BookingRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final PaymentGateway paymentGateway;

    public BookingService(BookingRepository bookingRepository, UserClient userClient, EventClient eventClient,
                          PaymentGateway paymentGateway) {
        this.bookingRepository = bookingRepository;
        this.userClient = userClient;
        this.eventClient = eventClient;
        this.paymentGateway = paymentGateway;
    }

    public BookingResponse create(CreateBookingRequest request) {
        verifyUserExists(request.userId());
        EventResponse event = fetchEventWithSeats(request.eventId(), request.numberOfTickets());

        BigDecimal totalPrice = event.ticketPrice().multiply(BigDecimal.valueOf(request.numberOfTickets()));
        Booking booking = bookingRepository.save(
                new Booking(request.userId(), request.eventId(), request.numberOfTickets(), totalPrice));

        return BookingResponse.from(processPayment(booking));
    }

    public BookingResponse pay(Long id) {
        Booking booking = getBooking(id);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException(id, booking.getStatus(), "paid");
        }
        fetchEventWithSeats(booking.getEventId(), booking.getNumberOfTickets());

        return BookingResponse.from(processPayment(booking));
    }

    public BookingResponse cancel(Long id) {
        Booking booking = getBooking(id);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException(id, booking.getStatus(), "cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(Long id) {
        return BookingResponse.from(getBooking(id));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findByUserId(Long userId) {
        return bookingRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    private Booking processPayment(Booking booking) {
        if (booking.getTotalPrice().signum() == 0) {
            booking.setStatus(BookingStatus.CONFIRMED);
            return bookingRepository.save(booking);
        }

        PaymentOutcome outcome = paymentGateway.pay(booking.getId(), booking.getTotalPrice());
        booking.setStatus(switch (outcome) {
            case PAID -> BookingStatus.CONFIRMED;
            case DECLINED -> BookingStatus.PAYMENT_FAILED;
            case UNAVAILABLE -> BookingStatus.PENDING_PAYMENT;
        });
        return bookingRepository.save(booking);
    }

    private Booking getBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    private void verifyUserExists(Long userId) {
        try {
            userClient.findById(userId);
        } catch (FeignException.NotFound ex) {
            throw new UserNotFoundException(userId);
        } catch (FeignException ex) {
            throw new DependencyUnavailableException("user-service", ex);
        }
    }

    private EventResponse fetchEventWithSeats(Long eventId, int numberOfTickets) {
        EventResponse event;
        try {
            event = eventClient.findById(eventId);
        } catch (FeignException.NotFound ex) {
            throw new EventNotFoundException(eventId);
        } catch (FeignException ex) {
            throw new DependencyUnavailableException("event-service", ex);
        }
        if (event.availableSeats() < numberOfTickets) {
            throw new InsufficientSeatsException(eventId, numberOfTickets, event.availableSeats());
        }
        return event;
    }

}
