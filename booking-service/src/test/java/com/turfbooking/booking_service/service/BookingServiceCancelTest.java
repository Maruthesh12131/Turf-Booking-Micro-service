package com.turfbooking.booking_service.service;

import com.turfbooking.booking_service.config.TestMessaging;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.entity.BookingItem;
import com.turfbooking.booking_service.enums.BookingItemStatus;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.event.ReleaseSlotsCommand;
import com.turfbooking.booking_service.exception.BookingConflictException;
import com.turfbooking.booking_service.exception.BookingNotFoundException;
import com.turfbooking.booking_service.outbox.OutboxService;
import com.turfbooking.booking_service.repository.BookingRepository;

class BookingServiceCancelTest {

    // 2026-10-27 10:00 at the turf (Asia/Kolkata). Cutoff is 60 minutes.
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
    static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    BookingRepository bookings = mock(BookingRepository.class);
    OutboxService outbox = mock(OutboxService.class);
    BookingService service = new BookingService(bookings, outbox, mock(PlatformTransactionManager.class), CLOCK, 1, 60, TestMessaging.defaults());

    Booking booking;

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setId(500L);
        booking.setCustomerId(42L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(new BigDecimal("750.00"));
        booking.setCurrentAmount(new BigDecimal("750.00"));
        // slot 1 starts 11:00 (exactly at the cutoff), slot 2 at 12:00, slot 3 at 10:30 (inside the cutoff)
        booking.addItem(active(1L, LocalDate.of(2026, 10, 27), "11:00"));
        booking.addItem(active(2L, LocalDate.of(2026, 10, 27), "12:00"));
        booking.addItem(active(3L, LocalDate.of(2026, 10, 27), "10:30"));
        when(bookings.findByIdForUpdate(500L)).thenReturn(Optional.of(booking));
    }

    static BookingItem active(long slotId, LocalDate date, String start) {
        BookingItem item = BookingItem.requested(slotId);
        item.setStatus(BookingItemStatus.ACTIVE);
        item.setSlotDate(date);
        item.setStartTime(LocalTime.parse(start));
        item.setEndTime(LocalTime.parse(start).plusMinutes(30));
        item.setPrice(new BigDecimal("250.00"));
        return item;
    }

    BookingItem item(long slotId) {
        return booking.getItems().stream().filter(i -> i.getSlotId() == slotId).findFirst().orElseThrow();
    }

    @Test
    void partialCancelMarksOnlyTheChosenSlotsPendingAndQueuesOneRelease() {
        assertThat(service.cancel(500L, 42L, List.of(2L, 1L))).isTrue();

        assertThat(item(1).getStatus()).isEqualTo(BookingItemStatus.RELEASE_PENDING);
        assertThat(item(2).getStatus()).isEqualTo(BookingItemStatus.RELEASE_PENDING);
        assertThat(item(3).getStatus()).isEqualTo(BookingItemStatus.ACTIVE);
        // still this booking's slots until turf-service confirms, and no money figure moves yet
        assertThat(item(1).getActiveSlotId()).isEqualTo(1L);
        assertThat(booking.getCurrentAmount()).isEqualByComparingTo("750.00");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(outbox).enqueue("booking.commands", "slots.release", "500", new ReleaseSlotsCommand(500L, List.of(1L, 2L)));
    }

    @Test
    void slotStartingExactlyAtTheCutoffCanBeCancelledOneInsideItCannot() {
        assertThatThrownBy(() -> service.cancel(500L, 42L, List.of(3L))) // 10:30, now is 10:00
                .isInstanceOf(BookingConflictException.class).hasMessageContaining("[3]");

        assertThat(service.cancel(500L, 42L, List.of(1L))).isTrue(); // 11:00
    }

    @Test
    void wholeBookingCancelIsAllOrNothingWhenOneSlotIsPastTheCutoff() {
        assertThatThrownBy(() -> service.cancel(500L, 42L, null))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("[3]").hasMessageContaining("cancelSlotIds");

        assertThat(booking.getItems()).allMatch(i -> i.getStatus() == BookingItemStatus.ACTIVE);
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void emptyListMeansTheWholeBooking() {
        item(3L).setStartTime(LocalTime.parse("15:00")); // make everything cancellable
        assertThat(service.cancel(500L, 42L, List.of())).isTrue();
        assertThat(booking.getItems()).allMatch(i -> i.getStatus() == BookingItemStatus.RELEASE_PENDING);
        verify(outbox).enqueue("booking.commands", "slots.release", "500", new ReleaseSlotsCommand(500L, List.of(1L, 2L, 3L)));
    }

    @Test
    void cancellingAgainWhileOrAfterItIsDoneIsAHarmlessNoOp() {
        service.cancel(500L, 42L, List.of(1L));
        assertThat(service.cancel(500L, 42L, List.of(1L))).isFalse();   // already being cancelled

        item(1L).release(BookingItemStatus.CANCELLED);
        item(1L).setCancelledAt(Instant.now());
        booking.getItems().stream().filter(i -> i.getStatus() == BookingItemStatus.RELEASE_PENDING).forEach(i -> i.setStatus(BookingItemStatus.ACTIVE));
        assertThat(service.cancel(500L, 42L, List.of(1L))).isFalse();   // already cancelled
        verify(outbox, org.mockito.Mockito.times(1)).enqueue(any(), any(), any(), any());
    }

    @Test
    void secondCancellationOfOtherSlotsWhileOneIsInProgressIsRefused() {
        service.cancel(500L, 42L, List.of(1L));
        assertThatThrownBy(() -> service.cancel(500L, 42L, List.of(2L)))
                .isInstanceOf(BookingConflictException.class).hasMessageContaining("in progress");
        assertThat(item(2).getStatus()).isEqualTo(BookingItemStatus.ACTIVE);
    }

    @Test
    void slotIdsOfOtherBookingsAreRejected() {
        assertThatThrownBy(() -> service.cancel(500L, 42L, List.of(1L, 999L))).isInstanceOf(IllegalArgumentException.class);
        assertThat(item(1).getStatus()).isEqualTo(BookingItemStatus.ACTIVE);
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void otherCustomersBookingLooksNonexistent() {
        assertThatThrownBy(() -> service.cancel(500L, 99L, List.of(1L))).isInstanceOf(BookingNotFoundException.class);
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void pendingAndRejectedBookingsCannotBeCancelled() {
        booking.setStatus(BookingStatus.PENDING_RESERVATION);
        assertThatThrownBy(() -> service.cancel(500L, 42L, null)).isInstanceOf(BookingConflictException.class);
        booking.setStatus(BookingStatus.REJECTED);
        assertThatThrownBy(() -> service.cancel(500L, 42L, null)).isInstanceOf(BookingConflictException.class);
    }

    @Test
    void ownerListingIsBounded() {
        assertThatThrownBy(() -> service.listOwnerBookings(1L, null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listOwnerBookings(1L, null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }
}
