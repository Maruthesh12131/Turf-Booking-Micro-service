package com.turfbooking.booking_service.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turfbooking.booking_service.config.MessagingProperties;
import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.entity.BookingItem;
import com.turfbooking.booking_service.entity.ProcessedMessage;
import com.turfbooking.booking_service.enums.BookingItemStatus;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.event.ConfirmReservationCommand;
import com.turfbooking.booking_service.event.ReleaseSlotsCommand;
import com.turfbooking.booking_service.event.SlotsRejectedEvent;
import com.turfbooking.booking_service.event.SlotsReleasedEvent;
import com.turfbooking.booking_service.event.SlotsReservedEvent;
import com.turfbooking.booking_service.outbox.OutboxService;
import com.turfbooking.booking_service.repository.BookingRepository;
import com.turfbooking.booking_service.repository.ProcessedMessageRepository;

/**
 * Applies turf-service's answers to bookings. Each answer is handled in one transaction that locks the booking, so it
 * is applied at most once however often the broker delivers it, and the follow-up command (confirm, or release to undo)
 * is queued in that same transaction.
 */
@Service
public class ReservationReplyService {

    private static final Logger log = LoggerFactory.getLogger(ReservationReplyService.class);
    private static final String INVALID = "INVALID";

    private final BookingRepository bookings;
    private final ProcessedMessageRepository processed;
    private final OutboxService outbox;
    private final MessagingProperties messaging;

    public ReservationReplyService(BookingRepository bookings, ProcessedMessageRepository processed, OutboxService outbox,
            MessagingProperties messaging) {
        this.messaging = messaging;
        this.bookings = bookings;
        this.processed = processed;
        this.outbox = outbox;
    }

    @Transactional
    public void onReserved(SlotsReservedEvent event, String messageId) {
        if (seen(messageId)) {
            return;
        }
        Booking booking = bookings.findByIdForUpdate(event.reservationId()).orElse(null);
        if (booking == null) {
            // Turf-service is holding slots for a booking we do not have. Give them back.
            log.warn("Reserved reply for unknown booking {}; releasing its slots", event.reservationId());
            queueRelease(event.reservationId());
        } else if (booking.getStatus() == BookingStatus.PENDING_RESERVATION) {
            confirmOrReject(booking, event);
        } else if (booking.getStatus() == BookingStatus.REJECTED) {
            // We gave up on this booking but turf-service still holds slots for it.
            queueRelease(booking.getId());
        }
        // Any other status: a repeat of an answer already applied.
        markSeen(messageId);
    }

    @Transactional
    public void onRejected(SlotsRejectedEvent event, String messageId) {
        if (seen(messageId)) {
            return;
        }
        Booking booking = bookings.findByIdForUpdate(event.reservationId()).orElse(null);
        if (booking != null && booking.getStatus() == BookingStatus.PENDING_RESERVATION) {
            reject(booking, event.code(), event.message());
        }
        markSeen(messageId);
    }

    @Transactional
    public void onReleased(SlotsReleasedEvent event, String messageId) {
        if (seen(messageId)) {
            return;
        }
        Booking booking = bookings.findByIdForUpdate(event.reservationId()).orElse(null);
        if (booking != null && event.slotIds() != null) {
            // Only slots the customer asked to cancel are waiting (RELEASE_PENDING); the release of a rejected
            // booking's slots finds none, because those items are already REJECTED. Repeats find nothing to do.
            boolean changed = false;
            for (BookingItem item : booking.getItems()) {
                if (item.getStatus() == BookingItemStatus.RELEASE_PENDING && event.slotIds().contains(item.getSlotId())) {
                    item.release(BookingItemStatus.CANCELLED);
                    changed = true;
                }
            }
            if (changed) {
                recalculate(booking);
            }
        }
        log.info("Turf-service released slots {} of reservation {}", event.slotIds(), event.reservationId());
        markSeen(messageId);
    }

    private void confirmOrReject(Booking booking, SlotsReservedEvent event) {
        Map<Long, BookingItem> items = booking.getItems().stream().collect(Collectors.toMap(BookingItem::getSlotId, Function.identity()));
        Set<Long> reserved = event.slots() == null ? Set.of()
                : event.slots().stream().map(SlotsReservedEvent.ReservedSlot::slotId).collect(Collectors.toCollection(HashSet::new));
        boolean consistent = !reserved.isEmpty() && reserved.equals(items.keySet())
                && event.slots().stream().allMatch(s -> s.price() != null && s.price().signum() >= 0 && s.slotDate() != null
                        && s.startTime() != null && s.endTime() != null && s.slotDate().equals(event.slotDate()));
        if (!consistent) {
            // Compensate: turf-service holds slots we never asked for in that shape, so undo it and fail the booking.
            log.error("Reservation reply for booking {} does not match the request; releasing", booking.getId());
            reject(booking, INVALID, "The reservation did not match the request");
            queueRelease(booking.getId());
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SlotsReservedEvent.ReservedSlot slot : event.slots()) {
            BookingItem item = items.get(slot.slotId());
            item.setSlotDate(slot.slotDate());
            item.setStartTime(slot.startTime());
            item.setEndTime(slot.endTime());
            item.setPrice(slot.price());
            item.setStatus(BookingItemStatus.ACTIVE);
            total = total.add(slot.price());
        }
        booking.setTurfId(event.turfId());
        booking.setOwnerId(event.ownerId());
        booking.setCourtNumber(event.courtNumber());
        booking.setBookingDate(event.slotDate());
        booking.setTotalAmount(total);
        booking.setCurrentAmount(total);
        booking.setStatus(BookingStatus.CONFIRMED);
        outbox.enqueue(messaging.bookingCommandsExchange(), messaging.confirmKey(), String.valueOf(booking.getId()),
                new ConfirmReservationCommand(booking.getId()));
    }

    // The remaining amount is the sum of the stored (agreed) prices of the slots still standing; the total never changes.
    private void recalculate(Booking booking) {
        BigDecimal remaining = BigDecimal.ZERO;
        boolean anyLive = false;
        boolean anyCancelled = false;
        for (BookingItem item : booking.getItems()) {
            if (item.getStatus() == BookingItemStatus.ACTIVE || item.getStatus() == BookingItemStatus.RELEASE_PENDING) {
                anyLive = true;
                remaining = remaining.add(item.getPrice());
            } else if (item.getStatus() == BookingItemStatus.CANCELLED) {
                anyCancelled = true;
            }
        }
        booking.setCurrentAmount(remaining);
        if (!anyLive && anyCancelled) {
            booking.setStatus(BookingStatus.CANCELLED);
        } else if (anyCancelled) {
            booking.setStatus(BookingStatus.PARTIALLY_CANCELLED);
        }
    }

    private void reject(Booking booking, String code, String message) {
        booking.getItems().forEach(item -> item.release(BookingItemStatus.REJECTED));
        booking.setStatus(BookingStatus.REJECTED);
        booking.setFailureCode(code);
        booking.setFailureReason(message);
    }

    private void queueRelease(Long bookingId) {
        outbox.enqueue(messaging.bookingCommandsExchange(), messaging.releaseKey(), String.valueOf(bookingId),
                new ReleaseSlotsCommand(bookingId, null));
    }

    private boolean seen(String messageId) {
        return messageId != null && processed.existsById(messageId);
    }

    private void markSeen(String messageId) {
        if (messageId != null) {
            processed.save(new ProcessedMessage(messageId));
        }
    }
}
