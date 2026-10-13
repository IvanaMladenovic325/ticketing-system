package com.ticketing.event.service;

import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.exception.EventNotFoundException;
import com.ticketing.event.exception.SoldSeatsConflictException;
import com.ticketing.event.model.Event;
import com.ticketing.event.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        Event event = new Event(
                request.name().trim(),
                trimToNull(request.description()),
                request.venue().trim(),
                request.dateTime(),
                request.totalSeats(),
                request.ticketPrice());
        return EventResponse.from(eventRepository.save(event));
    }

    public List<EventResponse> findAll() {
        return eventRepository.findAllByOrderByDateTimeAsc().stream()
                .map(EventResponse::from)
                .toList();
    }

    public EventResponse findById(Long id) {
        return EventResponse.from(getEvent(id));
    }

    @Transactional
    public EventResponse update(Long id, UpdateEventRequest request) {
        Event event = getEvent(id);
        if (request.totalSeats() < event.getSoldSeats()) {
            throw new SoldSeatsConflictException("Total seats cannot be less than already sold seats ("
                    + event.getSoldSeats() + ")");
        }
        event.setName(request.name().trim());
        event.setDescription(trimToNull(request.description()));
        event.setVenue(request.venue().trim());
        event.setDateTime(request.dateTime());
        event.setTicketPrice(request.ticketPrice());
        event.changeTotalSeats(request.totalSeats());
        return EventResponse.from(event);
    }

    @Transactional
    public void delete(Long id) {
        Event event = getEvent(id);
        if (event.getSoldSeats() > 0) {
            throw new SoldSeatsConflictException("Event with id " + id + " cannot be deleted, "
                    + event.getSoldSeats() + " seats are already sold");
        }
        eventRepository.delete(event);
    }

    private Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
