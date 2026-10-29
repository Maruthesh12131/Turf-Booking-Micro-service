package com.turfbooking.booking_service.service;

import com.turfbooking.booking_service.config.TestMessaging;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.entity.BookingItem;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.event.ReserveSlotsCommand;
import com.turfbooking.booking_service.outbox.OutboxRepository;
import com.turfbooking.booking_service.outbox.OutboxService;
import com.turfbooking.booking_service.repository.BookingRepository;

class ReservationRetryJobTest {

    static final Instant NOW = Instant.parse("2026-10-27T05:00:00Z");

    BookingRepository bookings = mock(BookingRepository.class);
    OutboxRepository outboxRepo = mock(OutboxRepository.class);
    OutboxService outbox = mock(OutboxService.class);
    ReservationRetryJob job = new ReservationRetryJob(bookings, outboxRepo, outbox, mock(PlatformTransactionManager.class),
            Clock.fixed(NOW, ZoneId.of("UTC")), 30, TestMessaging.defaults());

    Booking booking;

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setId(500L);
        booking.addItem(BookingItem.requested(2L));
        booking.addItem(BookingItem.requested(1L));
        booking.setReserveAttempts(1);
        booking.setLastCommandAt(NOW.minusSeconds(60));
        when(bookings.findByIdForUpdate(500L)).thenReturn(Optional.of(booking));
    }

    @Test
    void stalePendingBookingGetsTheSameReserveCommandAgain() {
        job.resend(500L, NOW.minusSeconds(30));

        verify(outbox).enqueue("booking.commands", "slots.reserve", "500", new ReserveSlotsCommand(500L, List.of(1L, 2L)));
        assertThat(booking.getReserveAttempts()).isEqualTo(2);
        assertThat(booking.getLastCommandAt()).isEqualTo(NOW);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_RESERVATION); // never released or failed by the timer
    }

    @Test
    void bookingThatGotItsAnswerInTheMeantimeIsLeftAlone() {
        booking.setStatus(BookingStatus.CONFIRMED);
        job.resend(500L, NOW.minusSeconds(30));
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void recentCommandIsNotRepeated() {
        booking.setLastCommandAt(NOW.minusSeconds(5));
        job.resend(500L, NOW.minusSeconds(30));
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void nothingIsQueuedWhileTheEarlierCommandIsStillWaitingInTheOutbox() {
        when(outboxRepo.existsByAggregateIdAndPublishedAtIsNull("500")).thenReturn(true);
        job.resend(500L, NOW.minusSeconds(30));
        verify(outbox, never()).enqueue(any(), any(), any(), any());
        assertThat(booking.getReserveAttempts()).isEqualTo(1);
    }

    @Test
    void runPicksUpOnlyStaleBookings() {
        when(bookings.findByStatusAndLastCommandAtBefore(eq(BookingStatus.PENDING_RESERVATION), eq(NOW.minusSeconds(30)), any()))
                .thenReturn(List.of(booking));
        job.run();
        verify(bookings).findByIdForUpdate(500L);
    }

    // ---- cancellations whose confirmation never came ----

    @Test
    void overdueCancellationAsksAgainForOnlyTheSlotsStillWaiting() {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.getItems().get(0).setStatus(com.turfbooking.booking_service.enums.BookingItemStatus.RELEASE_PENDING); // slot 2
        booking.getItems().get(1).setStatus(com.turfbooking.booking_service.enums.BookingItemStatus.ACTIVE);          // slot 1

        job.resendRelease(500L, NOW.minusSeconds(30));

        verify(outbox).enqueue("booking.commands", "slots.release", "500",
                new com.turfbooking.booking_service.event.ReleaseSlotsCommand(500L, List.of(2L)));
        assertThat(booking.getLastCommandAt()).isEqualTo(NOW);
    }

    @Test
    void cancellationThatFinishedOrWasRecentlyAskedIsNotRepeated() {
        job.resendRelease(500L, NOW.minusSeconds(30)); // no item waiting
        booking.getItems().get(0).setStatus(com.turfbooking.booking_service.enums.BookingItemStatus.RELEASE_PENDING);
        booking.setLastCommandAt(NOW.minusSeconds(5));
        job.resendRelease(500L, NOW.minusSeconds(30));
        booking.setLastCommandAt(NOW.minusSeconds(60));
        when(outboxRepo.existsByAggregateIdAndPublishedAtIsNull("500")).thenReturn(true);
        job.resendRelease(500L, NOW.minusSeconds(30));
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }
}
