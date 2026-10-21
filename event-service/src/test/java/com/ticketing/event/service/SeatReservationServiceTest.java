package com.ticketing.event.service;

import com.ticketing.event.exception.EventNotFoundException;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.SeatReservation;
import com.ticketing.event.model.SeatReservationStatus;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.SeatReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatReservationServiceTest {

    private static final long BOOKING_ID = 10L;
    private static final long EVENT_ID = 2L;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SeatReservationRepository reservationRepository;

    @InjectMocks
    private SeatReservationService service;

    @Test
    void reserveSeatsDecreasesAvailableSeatsAndRecordsReservation() {
        Event event = event(100);
        when(reservationRepository.existsById(BOOKING_ID)).thenReturn(false);
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));

        service.reserveSeats(BOOKING_ID, EVENT_ID, 3);

        assertThat(event.getAvailableSeats()).isEqualTo(97);
        ArgumentCaptor<SeatReservation> saved = ArgumentCaptor.forClass(SeatReservation.class);
        verify(reservationRepository).save(saved.capture());
        assertThat(saved.getValue().getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(saved.getValue().getStatus()).isEqualTo(SeatReservationStatus.RESERVED);
    }

    @Test
    void reserveSeatsSkipsAlreadyProcessedBooking() {
        when(reservationRepository.existsById(BOOKING_ID)).thenReturn(true);

        service.reserveSeats(BOOKING_ID, EVENT_ID, 3);

        verifyNoInteractions(eventRepository);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserveSeatsFailsWhenNotEnoughSeats() {
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(2)));

        assertThatThrownBy(() -> service.reserveSeats(BOOKING_ID, EVENT_ID, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only 2 available");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserveSeatsFailsWhenEventDoesNotExist() {
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveSeats(BOOKING_ID, EVENT_ID, 3))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void releaseSeatsReturnsSeatsAndMarksReservationReleased() {
        Event event = event(100);
        event.reserveSeats(3);
        SeatReservation reservation = new SeatReservation(BOOKING_ID, EVENT_ID, 3, SeatReservationStatus.RESERVED);
        when(reservationRepository.findById(BOOKING_ID)).thenReturn(Optional.of(reservation));
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));

        service.releaseSeats(BOOKING_ID, EVENT_ID, 3);

        assertThat(event.getAvailableSeats()).isEqualTo(100);
        assertThat(reservation.getStatus()).isEqualTo(SeatReservationStatus.RELEASED);
    }

    @Test
    void releaseSeatsSkipsAlreadyReleasedReservation() {
        SeatReservation reservation = new SeatReservation(BOOKING_ID, EVENT_ID, 3, SeatReservationStatus.RELEASED);
        when(reservationRepository.findById(BOOKING_ID)).thenReturn(Optional.of(reservation));

        service.releaseSeats(BOOKING_ID, EVENT_ID, 3);

        verifyNoInteractions(eventRepository);
    }

    @Test
    void releaseWithoutReservationRecordsReleasedSoLaterConfirmationIsSkipped() {
        Event event = event(100);
        when(reservationRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));

        service.releaseSeats(BOOKING_ID, EVENT_ID, 3);

        assertThat(event.getAvailableSeats()).isEqualTo(100);
        ArgumentCaptor<SeatReservation> saved = ArgumentCaptor.forClass(SeatReservation.class);
        verify(reservationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SeatReservationStatus.RELEASED);
    }

    private static Event event(int totalSeats) {
        return new Event("Koncert", null, "Arena", LocalDateTime.now().plusMonths(1),
                totalSeats, new BigDecimal("1500.00"));
    }

}
