package com.turfbooking.booking_service.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turfbooking.booking_service.client.OwnerClient;
import com.turfbooking.booking_service.dto.BookingResponseDTO;
import com.turfbooking.booking_service.dto.CancelBookingRequestDTO;
import com.turfbooking.booking_service.dto.OwnerBookingResponseDTO;
import com.turfbooking.booking_service.dto.CreateBookingRequestDTO;
import com.turfbooking.booking_service.dto.PagedResponse;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.exception.GlobalExceptionHandler;
import com.turfbooking.booking_service.security.AuthenticatedUser;
import com.turfbooking.booking_service.service.BookingService;

import jakarta.validation.Valid;

// Who is booking always comes from the validated token, never from the request.
@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final OwnerClient ownerClient;

    public BookingController(BookingService bookingService, OwnerClient ownerClient) {
        this.bookingService = bookingService;
        this.ownerClient = ownerClient;
    }

    /**
     * 201 booked; 409 slots no longer available; 400 invalid slots; 202 still being processed (poll GET /bookings/{id});
     * 200 when the same Idempotency-Key was already used and its booking is returned again.
     */
    @PostMapping
    public ResponseEntity<?> createBooking(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequestDTO request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        BookingService.Created created = bookingService.createBooking(user, idempotencyKey, request.slotIds());
        BookingStatus outcome = bookingService.awaitOutcome(created.bookingId());
        BookingResponseDTO booking = bookingService.getBooking(created.bookingId(), user.userId());
        URI location = URI.create("/bookings/" + created.bookingId());
        if (outcome == BookingStatus.PENDING_RESERVATION) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).location(location).body(booking);
        }
        if (outcome == BookingStatus.REJECTED) {
            boolean invalid = "INVALID".equals(bookingService.failureCode(created.bookingId()));
            ResponseEntity<Map<String, Object>> error = GlobalExceptionHandler.error(
                    invalid ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT, booking.failureReason());
            error.getBody().put("bookingId", created.bookingId());
            return error;
        }
        return ResponseEntity.status(created.replay() ? HttpStatus.OK : HttpStatus.CREATED).location(location).body(booking);
    }

    /**
     * Cancels the whole booking, or only the slots in cancelSlotIds. 200 when done, 202 when turf-service has not yet
     * confirmed freeing the slots (poll GET /bookings/{id}), 409 when it is too late or not allowed.
     */
    @PutMapping("/{bookingId}")
    public ResponseEntity<BookingResponseDTO> cancelBooking(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId,
            @Valid @RequestBody(required = false) CancelBookingRequestDTO request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        bookingService.cancel(bookingId, user.userId(), request == null ? null : request.cancelSlotIds());
        boolean done = bookingService.awaitCancellation(bookingId);
        BookingResponseDTO booking = bookingService.getBooking(bookingId, user.userId());
        return ResponseEntity.status(done ? HttpStatus.OK : HttpStatus.ACCEPTED).body(booking);
    }

    /** Owners only. The owner is identified from the token (through owner-service), never from the request. */
    @GetMapping("/owner")
    public ResponseEntity<PagedResponse<OwnerBookingResponseDTO>> ownerBookings(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long turfId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Long ownerId = ownerClient.resolveOwnerId(jwt.getTokenValue());
        return ResponseEntity.ok(bookingService.listOwnerBookings(ownerId, turfId, date, page, size));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponseDTO> getBooking(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId, AuthenticatedUser.from(jwt).userId()));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<BookingResponseDTO>> myBookings(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingService.listBookings(AuthenticatedUser.from(jwt).userId(), page, size));
    }
}
