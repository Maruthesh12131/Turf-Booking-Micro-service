package com.turfbooking.turf_service.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.turfbooking.turf_service.enums.ReservationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Turf-service's record of one booking's claim on slots, keyed by the booking id from booking-service. Because the
 * key is the booking id, handling the same command twice finds this row and repeats the stored outcome instead of
 * touching any slot again.
 */
@Entity
@Table(name = "slot_reservations")
@NoArgsConstructor
@Getter
@Setter
public class SlotReservation {

    @Id
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    // Every slot this reservation took, comma separated, kept even after slots are freed so history stays complete.
    @Column(length = 200)
    private String slotIds;

    // Why a REJECTED reservation was refused.
    @Column(length = 30)
    private String rejectCode;

    @Column(length = 300)
    private String rejectMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public SlotReservation(Long reservationId, ReservationStatus status) {
        this.reservationId = reservationId;
        this.status = status;
    }

    public List<Long> slotIdList() {
        if (slotIds == null || slotIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(slotIds.split(",")).map(Long::valueOf).toList();
    }

    public void setSlotIdList(List<Long> ids) {
        this.slotIds = ids == null ? null : String.join(",", ids.stream().map(String::valueOf).toList());
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
