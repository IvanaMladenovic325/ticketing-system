package com.ticketing.event.service;

import com.ticketing.event.exception.EventNotFoundException;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.SeatReservation;
import com.ticketing.event.model.SeatReservationStatus;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.SeatReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SeatReservationService {

    private static final Logger log = LoggerFactory.getLogger(SeatReservationService.class);

    private final EventRepository eventRepository;
    private final SeatReservationRepository reservationRepository;

    public SeatReservationService(EventRepository eventRepository,
                                  SeatReservationRepository reservationRepository) {
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public void reserveSeats(Long bookingId, Long eventId, int numberOfTickets) {
        if (reservationRepository.existsById(bookingId)) {
            log.info("Booking {} already processed, skipping seat reservation", bookingId);
            return;
        }
        Event event = lockEvent(eventId);
        event.reserveSeats(numberOfTickets);
        reservationRepository.save(
                new SeatReservation(bookingId, eventId, numberOfTickets, SeatReservationStatus.RESERVED));
        log.info("Reserved {} seats for booking {} on event {}, {} left",
                numberOfTickets, bookingId, eventId, event.getAvailableSeats());
    }

    @Transactional
    public void releaseSeats(Long bookingId, Long eventId, int numberOfTickets) {
        Optional<SeatReservation> existing = reservationRepository.findById(bookingId);
        if (existing.isEmpty()) {
            lockEvent(eventId);
            reservationRepository.save(
                    new SeatReservation(bookingId, eventId, numberOfTickets, SeatReservationStatus.RELEASED));
            log.info("Booking {} cancelled before seats were reserved, nothing to release", bookingId);
            return;
        }

        SeatReservation reservation = existing.get();
        if (reservation.getStatus() == SeatReservationStatus.RELEASED) {
            log.info("Seats for booking {} already released, skipping", bookingId);
            return;
        }
        Event event = lockEvent(reservation.getEventId());
        event.releaseSeats(reservation.getNumberOfTickets());
        reservation.markReleased();
        log.info("Released {} seats for booking {} on event {}, {} available",
                reservation.getNumberOfTickets(), bookingId, event.getId(), event.getAvailableSeats());
    }

    private Event lockEvent(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

}
