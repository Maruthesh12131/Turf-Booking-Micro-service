package com.turfbooking.booking_service.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.enums.PaymentStatus;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One customer's booking of slots on one court on one date. The id doubles as the reservation id turf-service
 * stores on the slots, so a slot can always be traced back to exactly one booking.
 *
 * Contact details, prices and times are copied into the booking when it is made, so later changes elsewhere
 * (a new price, a renamed account, a changed schedule) never alter what was agreed.
 */
@Entity
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_customer_key", columnNames = { "customer_id", "idempotency_key" }),
        indexes = { @Index(name = "idx_booking_owner", columnList = "owner_id"),
                @Index(name = "idx_booking_customer", columnList = "customer_id") })
@NoArgsConstructor
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    // Client-supplied key making POST /bookings safe to retry; requestHash detects the same key reused for a different request.
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING_RESERVATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_COLLECTED;

    // Filled from turf-service's reply once the slots are reserved.
    private Long turfId;

    @Column(name = "owner_id")
    private Long ownerId;

    private Long courtNumber;

    private LocalDate bookingDate;

    // Agreed total when the booking was made, and what remains after cancellations (both from slot price snapshots).
    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal currentAmount;

    // UNAVAILABLE (slots taken) or INVALID (bad request), plus the message shown to the customer.
    @Column(length = 30)
    private String failureCode;

    @Column(length = 500)
    private String failureReason;

    // How many times the reserve command was queued, and when last: a booking with no reply gets it queued again.
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int reserveAttempts;

    private Instant lastCommandAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    private List<BookingItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(BookingItem item) {
        item.setBooking(this);
        items.add(item);
    }
}
