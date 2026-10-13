package com.ticketing.event.service;

import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.exception.EventNotFoundException;
import com.ticketing.event.exception.SoldSeatsConflictException;
import com.ticketing.event.model.Event;
import com.ticketing.event.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final LocalDateTime DATE_TIME = LocalDateTime.now().plusMonths(1).withNano(0);

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void createSetsAvailableSeatsToTotalSeats() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.create(new CreateEventRequest(
                " Koncert ", "  ", " Arena ", DATE_TIME, 100, new BigDecimal("2500.00")));

        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Koncert");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().getAvailableSeats()).isEqualTo(100);
        assertThat(response.availableSeats()).isEqualTo(100);
    }

    @Test
    void findByIdThrowsWhenEventDoesNotExist() {
        when(eventRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.findById(42L))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void updateKeepsSoldSeatsWhenTotalSeatsChange() {
        Event existing = eventWithSoldSeats(100, 30);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(existing));

        EventResponse response = eventService.update(1L, updateRequest(150));

        assertThat(response.totalSeats()).isEqualTo(150);
        assertThat(response.availableSeats()).isEqualTo(120);
        assertThat(existing.getSoldSeats()).isEqualTo(30);
    }

    @Test
    void updateRejectsTotalSeatsBelowSoldSeats() {
        Event existing = eventWithSoldSeats(100, 30);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> eventService.update(1L, updateRequest(20)))
                .isInstanceOf(SoldSeatsConflictException.class)
                .hasMessageContaining("30");
        assertThat(existing.getTotalSeats()).isEqualTo(100);
        assertThat(existing.getName()).isEqualTo("Koncert");
    }

    @Test
    void deleteRemovesEventWithoutSoldSeats() {
        Event existing = eventWithSoldSeats(100, 0);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(existing));

        eventService.delete(1L);

        verify(eventRepository).delete(existing);
    }

    @Test
    void deleteRejectsEventWithSoldSeats() {
        Event existing = eventWithSoldSeats(100, 1);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> eventService.delete(1L))
                .isInstanceOf(SoldSeatsConflictException.class);
        verify(eventRepository, never()).delete(any());
    }

    private static Event eventWithSoldSeats(int totalSeats, int soldSeats) {
        Event event = new Event("Koncert", null, "Arena", DATE_TIME, totalSeats, new BigDecimal("2500.00"));
        ReflectionTestUtils.setField(event, "availableSeats", totalSeats - soldSeats);
        return event;
    }

    private static UpdateEventRequest updateRequest(int totalSeats) {
        return new UpdateEventRequest("Novi naziv", null, "Arena", DATE_TIME, totalSeats, new BigDecimal("3000.00"));
    }

}
