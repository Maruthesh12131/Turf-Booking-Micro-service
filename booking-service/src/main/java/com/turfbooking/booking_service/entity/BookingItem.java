package com.turfbooking.booking_service.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.Check;

import com.turfbooking.booking_service.enums.BookingItemStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One slot in a booking, with the date, time and price copied from turf-service when it was reserved. */
@Entity
@Table(name = "booking_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_item_booking_slot", columnNames = { "booking_id", "slot_id" }),
                // Two bookings can never both hold the same slot: at most one live item per slot.
                @UniqueConstraint(name = "uk_item_active_slot", columnNames = { "active_slot_id" }) })
// active_slot_id equals slot_id while the item still counts (PENDING, ACTIVE, RELEASE_PENDING) and is null once it
// is CANCELLED or REJECTED, which frees the slot for a later booking without deleting this history row.
@Check(name = "chk_item_active_slot",
        constraints = "(status in ('PENDING', 'ACTIVE', 'RELEASE_PENDING')) = (active_slot_id is not null)"
                + " and (active_slot_id is null or active_slot_id = slot_id)")
@NoArgsConstructor
@Getter
@Setter
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // Id of the slot in turf-service (a plain number, not a foreign key: that data lives in another service).
    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "active_slot_id")
    private Long activeSlotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingItemStatus status = BookingItemStatus.PENDING;

    // Snapshot from turf-service's reservation reply; empty until the slot is reserved.
    private LocalDate slotDate;

    private LocalTime startTime;

    private LocalTime endTime;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    private Instant cancelledAt;

    /** New item for a requested slot: it counts against that slot from the moment it is saved. */
    public static BookingItem requested(Long slotId) {
        BookingItem item = new BookingItem();
        item.slotId = slotId;
        item.activeSlotId = slotId;
        return item;
    }

    /** Moves to a status that no longer holds the slot, freeing it for other bookings. */
    public void release(BookingItemStatus finalStatus) {
        if (finalStatus != BookingItemStatus.CANCELLED && finalStatus != BookingItemStatus.REJECTED) {
            throw new IllegalArgumentException("Not a releasing status: " + finalStatus);
        }
        status = finalStatus;
        activeSlotId = null;
        if (finalStatus == BookingItemStatus.CANCELLED) {
            cancelledAt = Instant.now();
        }
    }
}
