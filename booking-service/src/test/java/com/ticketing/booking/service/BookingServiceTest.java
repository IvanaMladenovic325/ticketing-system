package com.ticketing.booking.service;

import com.ticketing.booking.client.EventClient;
import com.ticketing.booking.client.EventResponse;
import com.ticketing.booking.client.PaymentGateway;
import com.ticketing.booking.client.PaymentOutcome;
import com.ticketing.booking.client.UserClient;
import com.ticketing.booking.client.UserResponse;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.dto.CreateBookingRequest;
import com.ticketing.booking.exception.BookingNotFoundException;
import com.ticketing.booking.exception.DependencyUnavailableException;
import com.ticketing.booking.exception.EventNotFoundException;
import com.ticketing.booking.exception.InsufficientSeatsException;
import com.ticketing.booking.exception.InvalidBookingStateException;
import com.ticketing.booking.exception.UserNotFoundException;
import com.ticketing.booking.messaging.BookingEventPublisher;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingStatus;
import com.ticketing.booking.repository.BookingRepository;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final CreateBookingRequest REQUEST = new CreateBookingRequest(1L, 2L, 3);
    private static final UserResponse USER = new UserResponse(1L, "Ana", "Anić", "ana@example.com");
    private static final EventResponse EVENT = new EventResponse(2L, "Koncert", 100, new BigDecimal("1500.00"));

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private EventClient eventClient;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private BookingEventPublisher eventPublisher;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void saveAssignsId() {
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            if (booking.getId() == null) {
                ReflectionTestUtils.setField(booking, "id", 10L);
            }
            return booking;
        });
    }

    @Test
    void createConfirmsBookingWhenPaymentSucceeds() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenReturn(EVENT);
        when(paymentGateway.pay(10L, new BigDecimal("4500.00"))).thenReturn(PaymentOutcome.PAID);

        BookingResponse response = bookingService.create(REQUEST);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.totalPrice()).isEqualByComparingTo("4500.00");
        verify(eventPublisher).publishConfirmed(argThat(booking -> booking.getId() == 10L));
    }

    @Test
    void createKeepsBookingPendingWhenPaymentServiceIsUnavailable() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenReturn(EVENT);
        when(paymentGateway.pay(anyLong(), any())).thenReturn(PaymentOutcome.UNAVAILABLE);

        BookingResponse response = bookingService.create(REQUEST);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createMarksPaymentFailedWhenPaymentIsDeclined() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenReturn(EVENT);
        when(paymentGateway.pay(anyLong(), any())).thenReturn(PaymentOutcome.DECLINED);

        BookingResponse response = bookingService.create(REQUEST);

        assertThat(response.status()).isEqualTo(BookingStatus.PAYMENT_FAILED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createConfirmsFreeEventWithoutCallingPayment() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenReturn(new EventResponse(2L, "Besplatno", 100, BigDecimal.ZERO));

        BookingResponse response = bookingService.create(REQUEST);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(paymentGateway);
        verify(eventPublisher).publishConfirmed(any(Booking.class));
    }

    @Test
    void createThrowsWhenUserDoesNotExist() {
        when(userClient.findById(1L)).thenThrow(feignException(404));

        assertThatThrownBy(() -> bookingService.create(REQUEST))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(eventClient, paymentGateway);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenEventDoesNotExist() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenThrow(feignException(404));

        assertThatThrownBy(() -> bookingService.create(REQUEST))
                .isInstanceOf(EventNotFoundException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenNotEnoughSeats() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenReturn(new EventResponse(2L, "Koncert", 2, new BigDecimal("1500.00")));

        assertThatThrownBy(() -> bookingService.create(REQUEST))
                .isInstanceOf(InsufficientSeatsException.class)
                .hasMessageContaining("2 available seats");

        verifyNoInteractions(paymentGateway);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenUserServiceFails() {
        when(userClient.findById(1L)).thenThrow(feignException(500));

        assertThatThrownBy(() -> bookingService.create(REQUEST))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("user-service");
    }

    @Test
    void payConfirmsPendingBooking() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(eventClient.findById(2L)).thenReturn(EVENT);
        when(paymentGateway.pay(10L, new BigDecimal("4500.00"))).thenReturn(PaymentOutcome.PAID);

        BookingResponse response = bookingService.pay(10L);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        verify(eventPublisher).publishConfirmed(booking);
    }

    @Test
    void payRejectsBookingThatIsNotPending() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.pay(10L))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("CONFIRMED");

        verifyNoInteractions(eventClient, paymentGateway);
    }

    @Test
    void payRechecksSeatsBeforeCharging() {
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pendingBooking()));
        when(eventClient.findById(2L)).thenReturn(new EventResponse(2L, "Koncert", 0, new BigDecimal("1500.00")));

        assertThatThrownBy(() -> bookingService.pay(10L))
                .isInstanceOf(InsufficientSeatsException.class);

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void cancelChangesStatusToCancelled() {
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pendingBooking()));

        BookingResponse response = bookingService.cancel(10L);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        verify(eventPublisher).publishCancelled(any(Booking.class));
    }

    @Test
    void cancelRejectsAlreadyCancelledBooking() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(10L))
                .isInstanceOf(InvalidBookingStateException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void findByIdThrowsWhenBookingDoesNotExist() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.findById(99L))
                .isInstanceOf(BookingNotFoundException.class);
    }

    private static Booking pendingBooking() {
        Booking booking = new Booking(1L, 2L, 3, new BigDecimal("4500.00"));
        ReflectionTestUtils.setField(booking, "id", 10L);
        return booking;
    }

    private static FeignException feignException(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/test", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("test", Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(Map.of())
                .build());
    }

}
