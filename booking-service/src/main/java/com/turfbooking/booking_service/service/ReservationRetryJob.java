package com.turfbooking.booking_service.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.turfbooking.booking_service.config.MessagingProperties;
import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.enums.BookingItemStatus;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.event.ReleaseSlotsCommand;
import com.turfbooking.booking_service.event.ReserveSlotsCommand;
import com.turfbooking.booking_service.outbox.OutboxRepository;
import com.turfbooking.booking_service.outbox.OutboxService;
import com.turfbooking.booking_service.repository.BookingRepository;

/**
 * Recovers bookings whose answer never arrived (turf-service down, a lost message, a crash after the command was sent).
 * It queues the same reserve command again; turf-service replies to a repeat with the original outcome, so this can
 * never reserve twice. It never releases slots: only turf-service's answer (or the booking being rejected) decides that.
 */
@Component
public class ReservationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationRetryJob.class);

    private final BookingRepository bookings;
    private final OutboxRepository outboxRepository;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration retryAfter;
    private final MessagingProperties messaging;

    public ReservationRetryJob(BookingRepository bookings, OutboxRepository outboxRepository, OutboxService outbox,
            PlatformTransactionManager txManager, Clock clock,
            @Value("${booking.reserve-retry-after-seconds:30}") long retryAfterSeconds,
            MessagingProperties messaging) {
        this.messaging = messaging;
        this.bookings = bookings;
        this.outboxRepository = outboxRepository;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.retryAfter = Duration.ofSeconds(retryAfterSeconds);
    }

    @Scheduled(fixedDelayString = "${booking.reserve-retry-interval-ms:10000}")
    public void run() {
        Instant cutoff = Instant.now(clock).minus(retryAfter);
        List<Booking> stale = bookings.findByStatusAndLastCommandAtBefore(BookingStatus.PENDING_RESERVATION, cutoff, PageRequest.of(0, 50));
        for (Booking candidate : stale) {
            tx.executeWithoutResult(status -> resend(candidate.getId(), cutoff));
        }
        for (Booking candidate : bookings.findWithOverdueRelease(cutoff, PageRequest.of(0, 50))) {
            tx.executeWithoutResult(status -> resendRelease(candidate.getId(), cutoff));
        }
    }

    /** Same idea for a cancellation whose confirmation never came: ask again for the slots still waiting. */
    void resendRelease(Long bookingId, Instant cutoff) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getLastCommandAt() == null || !booking.getLastCommandAt().isBefore(cutoff)
                || outboxRepository.existsByAggregateIdAndPublishedAtIsNull(String.valueOf(bookingId))) {
            return;
        }
        List<Long> waiting = booking.getItems().stream().filter(i -> i.getStatus() == BookingItemStatus.RELEASE_PENDING)
                .map(i -> i.getSlotId()).sorted().toList();
        if (waiting.isEmpty()) {
            return;
        }
        outbox.enqueue(messaging.bookingCommandsExchange(), messaging.releaseKey(), String.valueOf(bookingId),
                new ReleaseSlotsCommand(bookingId, waiting));
        booking.setLastCommandAt(Instant.now(clock));
    }

    void resend(Long bookingId, Instant cutoff) {
        // Re-read under lock: the answer may have just arrived.
        Booking booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_RESERVATION
                || booking.getLastCommandAt() == null || !booking.getLastCommandAt().isBefore(cutoff)) {
            return;
        }
        // If the earlier command has not even left our outbox (broker down), queueing another adds nothing.
        if (outboxRepository.existsByAggregateIdAndPublishedAtIsNull(String.valueOf(bookingId))) {
            return;
        }
        List<Long> slotIds = booking.getItems().stream().map(i -> i.getSlotId()).sorted().toList();
        outbox.enqueue(messaging.bookingCommandsExchange(), messaging.reserveKey(), String.valueOf(bookingId),
                new ReserveSlotsCommand(bookingId, slotIds));
        booking.setReserveAttempts(booking.getReserveAttempts() + 1);
        booking.setLastCommandAt(Instant.now(clock));
        if (booking.getReserveAttempts() % 10 == 0) {
            log.warn("Booking {} still has no answer from turf-service after {} attempts", bookingId, booking.getReserveAttempts());
        }
    }
}
