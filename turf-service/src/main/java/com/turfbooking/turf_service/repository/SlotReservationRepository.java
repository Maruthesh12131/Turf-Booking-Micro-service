package com.turfbooking.turf_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turfbooking.turf_service.entity.SlotReservation;

import jakarta.persistence.LockModeType;

public interface SlotReservationRepository extends JpaRepository<SlotReservation, Long> {

    // Confirm and release lock the reservation first, so they cannot interleave with each other for the same booking.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SlotReservation r where r.reservationId = :id")
    Optional<SlotReservation> findByIdForUpdate(@Param("id") Long id);
}
