package com.turfbooking.booking_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.entity.BookingItem;
import com.turfbooking.booking_service.enums.BookingItemStatus;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.enums.PaymentStatus;

/** What a customer sees of their booking. paymentStatus says honestly that no money has been collected. */
public record BookingResponseDTO(Long bookingId, BookingStatus status, PaymentStatus paymentStatus, Long turfId,
        Long courtNumber, LocalDate bookingDate, BigDecimal totalAmount, BigDecimal currentAmount, String failureReason,
        List<Item> items, Instant createdAt) {

    public record Item(Long slotId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, BigDecimal price,
            BookingItemStatus status) {
    }

    public static BookingResponseDTO from(Booking b) {
        List<Item> items = b.getItems().stream()
                .sorted(Comparator.comparing(BookingItem::getId))
                .map(i -> new Item(i.getSlotId(), i.getSlotDate(), i.getStartTime(), i.getEndTime(), i.getPrice(), i.getStatus()))
                .toList();
        return new BookingResponseDTO(b.getId(), b.getStatus(), b.getPaymentStatus(), b.getTurfId(), b.getCourtNumber(),
                b.getBookingDate(), b.getTotalAmount(), b.getCurrentAmount(), b.getFailureReason(), items, b.getCreatedAt());
    }
}
