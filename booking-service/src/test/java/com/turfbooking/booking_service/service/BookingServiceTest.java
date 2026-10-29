package com.turfbooking.booking_service.service;

import com.turfbooking.booking_service.config.TestMessaging;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.enums.BookingItemStatus;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.event.ReserveSlotsCommand;
import com.turfbooking.booking_service.exception.BookingNotFoundException;
import com.turfbooking.booking_service.exception.IdempotencyConflictException;
import com.turfbooking.booking_service.exception.SlotsConflictException;
import com.turfbooking.booking_service.outbox.OutboxService;
import com.turfbooking.booking_service.repository.BookingRepository;
import com.turfbooking.booking_service.security.AuthenticatedUser;

class BookingServiceTest {

    static final AuthenticatedUser USER = new AuthenticatedUser(42L, "c@x.com", "Cathy", "+919876543210");

    BookingRepository bookings = mock(BookingRepository.class);
    OutboxService outbox = mock(OutboxService.class);
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    BookingService service;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new BookingService(bookings, outbox, txManager, Clock.fixed(Instant.parse("2026-10-27T04:30:00Z"), ZoneId.of("UTC")), 1, 60, TestMessaging.defaults());
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(500L);
            return b;
        });
    }

    @Test
    void newBookingIsPendingWithCustomerSnapshotAndALiveItemPerSlot() {
        BookingService.Created created = service.createBooking(USER, "key-1", List.of(3L, 1L));

        assertThat(created).isEqualTo(new BookingService.Created(500L, false));
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).saveAndFlush(saved.capture());
        Booking b = saved.getValue();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING_RESERVATION);
        assertThat(b.getCustomerId()).isEqualTo(42L);
        assertThat(b.getCustomerName()).isEqualTo("Cathy");
        assertThat(b.getCustomerPhone()).isEqualTo("+919876543210");
        assertThat(b.getItems()).extracting("slotId").containsExactly(3L, 1L);
        assertThat(b.getItems()).allMatch(i -> i.getStatus() == BookingItemStatus.PENDING && i.getActiveSlotId().equals(i.getSlotId()));
        assertThat(b.getTotalAmount()).isNull(); // priced only from turf-service's answer
    }

    @Test
    void reserveCommandIsQueuedInTheSameTransactionAsTheBooking() {
        service.createBooking(USER, "key-1", List.of(3L, 1L));

        ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(eq("booking.commands"), eq("slots.reserve"), eq("500"), command.capture());
        assertThat(command.getValue()).isEqualTo(new ReserveSlotsCommand(500L, List.of(3L, 1L)));
    }

    @Test
    void sameKeyAndSlotsReturnsTheExistingBookingWithoutMakingAnother() {
        Booking existing = new Booking();
        existing.setId(77L);
        existing.setRequestHash(BookingService.requestHash(List.of(1L, 3L)));
        when(bookings.findByCustomerIdAndIdempotencyKey(42L, "key-1")).thenReturn(Optional.of(existing));

        // a re-ordered list is still the same request
        assertThat(service.createBooking(USER, "key-1", List.of(3L, 1L))).isEqualTo(new BookingService.Created(77L, true));
        verify(bookings, never()).saveAndFlush(any());
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void sameKeyWithDifferentSlotsIsRefused() {
        Booking existing = new Booking();
        existing.setId(77L);
        existing.setRequestHash(BookingService.requestHash(List.of(1L)));
        when(bookings.findByCustomerIdAndIdempotencyKey(42L, "key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createBooking(USER, "key-1", List.of(2L))).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void slotAlreadyInAnotherLiveBookingIsAConflictAndQueuesNothing() {
        when(bookings.saveAndFlush(any(Booking.class))).thenThrow(new DataIntegrityViolationException("uk_item_active_slot"));

        assertThatThrownBy(() -> service.createBooking(USER, "key-1", List.of(1L))).isInstanceOf(SlotsConflictException.class);
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void twoIdenticalRequestsRacingResolveToTheWinnersBooking() {
        Booking winner = new Booking();
        winner.setId(88L);
        winner.setRequestHash(BookingService.requestHash(List.of(1L)));
        when(bookings.findByCustomerIdAndIdempotencyKey(42L, "key-1")).thenReturn(Optional.empty(), Optional.of(winner));
        when(bookings.saveAndFlush(any(Booking.class))).thenThrow(new DataIntegrityViolationException("uk_booking_customer_key"));

        assertThat(service.createBooking(USER, "key-1", List.of(1L))).isEqualTo(new BookingService.Created(88L, true));
    }

    @Test
    void badKeysAndDuplicateSlotsAreRejectedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> service.createBooking(USER, null, List.of(1L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBooking(USER, " ", List.of(1L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBooking(USER, "k".repeat(101), List.of(1L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBooking(USER, "k", List.of(1L, 1L))).isInstanceOf(IllegalArgumentException.class);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void anotherCustomersBookingLooksNonexistent() {
        Booking b = new Booking();
        b.setId(5L);
        b.setCustomerId(99L);
        when(bookings.findById(5L)).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> service.getBooking(5L, 42L)).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void awaitReturnsAsSoonAsTheStatusChangesAndGivesUpAfterTheWaitTime() {
        when(bookings.findStatusById(5L)).thenReturn(Optional.of(BookingStatus.PENDING_RESERVATION),
                Optional.of(BookingStatus.PENDING_RESERVATION), Optional.of(BookingStatus.CONFIRMED));
        assertThat(service.awaitOutcome(5L)).isEqualTo(BookingStatus.CONFIRMED);

        when(bookings.findStatusById(6L)).thenReturn(Optional.of(BookingStatus.PENDING_RESERVATION));
        long t0 = System.currentTimeMillis();
        assertThat(service.awaitOutcome(6L)).isEqualTo(BookingStatus.PENDING_RESERVATION);
        assertThat(System.currentTimeMillis() - t0).isBetween(900L, 2500L); // wait time is 1s in this test
    }

    @Test
    void listingIsBoundedAndPaged() {
        assertThatThrownBy(() -> service.listBookings(42L, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listBookings(42L, 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }
}
