package com.turfbooking.booking_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.turfbooking.booking_service.config.MessagingProperties;
import com.turfbooking.booking_service.dto.BookingResponseDTO;
import com.turfbooking.booking_service.dto.OwnerBookingResponseDTO;
import com.turfbooking.booking_service.dto.PagedResponse;
import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.entity.BookingItem;
import com.turfbooking.booking_service.enums.BookingItemStatus;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.event.ReleaseSlotsCommand;
import com.turfbooking.booking_service.event.ReserveSlotsCommand;
import com.turfbooking.booking_service.exception.BookingConflictException;
import com.turfbooking.booking_service.exception.BookingNotFoundException;
import com.turfbooking.booking_service.exception.IdempotencyConflictException;
import com.turfbooking.booking_service.exception.SlotsConflictException;
import com.turfbooking.booking_service.outbox.OutboxService;
import com.turfbooking.booking_service.repository.BookingRepository;
import com.turfbooking.booking_service.security.AuthenticatedUser;

@Service
public class BookingService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEY_LENGTH = 100;

    public record Created(Long bookingId, boolean replay) {
    }

    private final BookingRepository bookings;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration waitTime;
    private final Duration cancelCutoff;
    private final MessagingProperties messaging;

    public BookingService(BookingRepository bookings, OutboxService outbox, PlatformTransactionManager txManager,
            Clock clock, @Value("${booking.wait-seconds:5}") long waitSeconds,
            @Value("${booking.cancel-cutoff-minutes:60}") long cancelCutoffMinutes, MessagingProperties messaging) {
        this.messaging = messaging;
        this.cancelCutoff = Duration.ofMinutes(cancelCutoffMinutes);
        this.bookings = bookings;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.waitTime = Duration.ofSeconds(waitSeconds);
    }

    /**
     * Saves the booking as PENDING_RESERVATION together with the "reserve these slots" message, in one transaction.
     * Nothing has been reserved yet; turf-service decides that and the booking is updated when it answers.
     * Sending the same Idempotency-Key and slots again returns the existing booking instead of making a second one.
     */
    public Created createBooking(AuthenticatedUser user, String idempotencyKey, List<Long> slotIds) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key header is required (at most " + MAX_KEY_LENGTH + " characters)");
        }
        if (slotIds == null || new TreeSet<>(slotIds).size() != slotIds.size()) {
            throw new IllegalArgumentException("slotIds must not contain duplicates");
        }
        String hash = requestHash(slotIds);
        try {
            return tx.execute(status -> {
                Booking existing = bookings.findByCustomerIdAndIdempotencyKey(user.userId(), idempotencyKey).orElse(null);
                if (existing != null) {
                    return replayOf(existing, hash);
                }
                Booking booking = new Booking();
                booking.setCustomerId(user.userId());
                booking.setCustomerEmail(user.email());
                booking.setCustomerName(user.name());
                booking.setCustomerPhone(user.phone());
                booking.setIdempotencyKey(idempotencyKey);
                booking.setRequestHash(hash);
                slotIds.forEach(id -> booking.addItem(BookingItem.requested(id)));
                booking.setReserveAttempts(1);
                booking.setLastCommandAt(Instant.now(clock));
                // Flushed now so a slot already claimed by another live booking fails here, before anything is queued.
                Booking saved = bookings.saveAndFlush(booking);
                outbox.enqueue(messaging.bookingCommandsExchange(), messaging.reserveKey(),
                        String.valueOf(saved.getId()), new ReserveSlotsCommand(saved.getId(), List.copyOf(slotIds)));
                return new Created(saved.getId(), false);
            });
        } catch (DataIntegrityViolationException e) {
            // Either the same request arrived twice at once (the other one won), or a slot is in another live booking.
            Booking existing = tx.execute(status ->
                    bookings.findByCustomerIdAndIdempotencyKey(user.userId(), idempotencyKey).orElse(null));
            if (existing != null) {
                return replayOf(existing, hash);
            }
            throw new SlotsConflictException();
        }
    }

    private Created replayOf(Booking existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new IdempotencyConflictException();
        }
        return new Created(existing.getId(), true);
    }

    /** Waits (up to booking.wait-seconds) for turf-service's answer; returns the status last seen. */
    public BookingStatus awaitOutcome(Long bookingId) {
        long deadline = System.nanoTime() + waitTime.toNanos();
        while (true) {
            // A scalar query always reads the database, never a cached entity.
            BookingStatus status = bookings.findStatusById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
            if (status != BookingStatus.PENDING_RESERVATION || System.nanoTime() >= deadline) {
                return status;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return status;
            }
        }
    }

    /**
     * Cancels the whole booking (cancelSlotIds null or empty) or just some of its slots. Nothing is cancelled here yet:
     * the chosen slots are marked RELEASE_PENDING and a release command is queued in the same transaction. They only
     * become CANCELLED, and the amount is recalculated, when turf-service confirms it freed them. Until then the slots
     * still count as this booking's, so nobody else can take them and a failure can simply be retried.
     *
     * The agreed prices are never touched: the remaining amount is the sum of the stored prices of the remaining slots.
     * It is all-or-nothing: if any chosen slot is past the cutoff, nothing is cancelled.
     *
     * @return true if a release was queued, false if there was nothing left to cancel (repeating a cancel is harmless)
     */
    @Transactional
    public boolean cancel(Long bookingId, Long customerId, List<Long> cancelSlotIds) {
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .filter(b -> b.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        switch (booking.getStatus()) {
            case PENDING_RESERVATION -> throw new BookingConflictException("The booking is still being processed; try again shortly");
            case REJECTED -> throw new BookingConflictException("This booking was not confirmed, so there is nothing to cancel");
            default -> { /* CONFIRMED, PARTIALLY_CANCELLED, CANCELLED */ }
        }
        boolean wholeBooking = cancelSlotIds == null || cancelSlotIds.isEmpty();
        Set<Long> requested = wholeBooking ? null : new HashSet<>(cancelSlotIds);
        if (!wholeBooking) {
            Set<Long> known = booking.getItems().stream().map(BookingItem::getSlotId).collect(Collectors.toSet());
            if (!known.containsAll(requested)) {
                throw new IllegalArgumentException("cancelSlotIds must be slots of this booking");
            }
        }
        List<BookingItem> targets = booking.getItems().stream()
                .filter(i -> wholeBooking || requested.contains(i.getSlotId()))
                .filter(i -> i.getStatus() == BookingItemStatus.ACTIVE || i.getStatus() == BookingItemStatus.RELEASE_PENDING)
                .toList();
        if (targets.stream().allMatch(i -> i.getStatus() == BookingItemStatus.RELEASE_PENDING)) {
            return false; // already cancelled, or already being cancelled
        }
        if (targets.stream().anyMatch(i -> i.getStatus() == BookingItemStatus.RELEASE_PENDING)
                || booking.getItems().stream().anyMatch(i -> i.getStatus() == BookingItemStatus.RELEASE_PENDING)) {
            throw new BookingConflictException("A cancellation of this booking is already in progress");
        }
        List<Long> tooLate = targets.stream()
                .filter(i -> LocalDateTime.of(i.getSlotDate(), i.getStartTime()).isBefore(LocalDateTime.now(clock).plus(cancelCutoff)))
                .map(BookingItem::getSlotId).toList();
        if (!tooLate.isEmpty()) {
            throw new BookingConflictException("Slots can only be cancelled until " + cancelCutoff.toMinutes()
                    + " minutes before they start; too late for slots " + tooLate
                    + (wholeBooking ? " (cancel the others with cancelSlotIds)" : ""));
        }
        targets.forEach(i -> i.setStatus(BookingItemStatus.RELEASE_PENDING));
        booking.setLastCommandAt(Instant.now(clock));
        List<Long> slotIds = targets.stream().map(BookingItem::getSlotId).sorted().toList();
        outbox.enqueue(messaging.bookingCommandsExchange(), messaging.releaseKey(), String.valueOf(bookingId),
                new ReleaseSlotsCommand(bookingId, slotIds));
        return true;
    }

    /** Waits (up to booking.wait-seconds) until turf-service has confirmed every pending release of the booking. */
    public boolean awaitCancellation(Long bookingId) {
        long deadline = System.nanoTime() + waitTime.toNanos();
        while (true) {
            boolean done = bookings.countPendingReleases(bookingId) == 0;
            if (done || System.nanoTime() >= deadline) {
                return done;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** Bookings on the given owner's turfs only; the owner id is never taken from the request. */
    @Transactional(readOnly = true)
    public PagedResponse<OwnerBookingResponseDTO> listOwnerBookings(Long ownerId, Long turfId, LocalDate date, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Specification<Booking> spec = (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
        if (turfId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("turfId"), turfId));
        }
        if (date != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("bookingDate"), date));
        }
        Page<Booking> result = bookings.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "bookingDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PagedResponse.of(result, result.getContent().stream().map(OwnerBookingResponseDTO::from).toList());
    }

    /** Another customer's booking looks exactly like one that does not exist. */
    @Transactional(readOnly = true)
    public BookingResponseDTO getBooking(Long bookingId, Long customerId) {
        Booking booking = bookings.findById(bookingId)
                .filter(b -> b.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return BookingResponseDTO.from(booking);
    }

    /** The caller's failure details, needed to pick the HTTP status of a rejected booking. */
    @Transactional(readOnly = true)
    public String failureCode(Long bookingId) {
        return bookings.findById(bookingId).map(Booking::getFailureCode).orElse(null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BookingResponseDTO> listBookings(Long customerId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<Booking> result = bookings.findByCustomerId(customerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return PagedResponse.of(result, result.getContent().stream().map(BookingResponseDTO::from).toList());
    }

    // Same slots in any order hash the same, so a retry with a re-ordered list is still the same request.
    static String requestHash(List<Long> slotIds) {
        try {
            String canonical = new TreeSet<>(slotIds).toString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
