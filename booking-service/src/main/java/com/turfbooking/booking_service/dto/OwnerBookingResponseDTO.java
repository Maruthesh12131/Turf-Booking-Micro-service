package com.turfbooking.booking_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.entity.BookingItem;
import com.turfbooking.booking_service.enums.BookingStatus;

/** What a turf owner sees of a booking on their turf: the customer's contact details as given when booking. */
public record OwnerBookingResponseDTO(Long bookingId, BookingStatus status, Long turfId, Long courtNumber,
        LocalDate bookingDate, String customerName, String customerPhone, BigDecimal totalAmount,
        BigDecimal currentAmount, List<BookingResponseDTO.Item> items, Instant createdAt) {

    public static OwnerBookingResponseDTO from(Booking b) {
        List<BookingResponseDTO.Item> items = b.getItems().stream()
                .sorted(Comparator.comparing(BookingItem::getId))
                .map(i -> new BookingResponseDTO.Item(i.getSlotId(), i.getSlotDate(), i.getStartTime(), i.getEndTime(),
                        i.getPrice(), i.getStatus()))
                .toList();
        return new OwnerBookingResponseDTO(b.getId(), b.getStatus(), b.getTurfId(), b.getCourtNumber(), b.getBookingDate(),
                b.getCustomerName(), b.getCustomerPhone(), b.getTotalAmount(), b.getCurrentAmount(), items, b.getCreatedAt());
    }
}
