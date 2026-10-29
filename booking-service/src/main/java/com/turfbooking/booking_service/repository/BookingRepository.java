package com.turfbooking.booking_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.enums.BookingStatus;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    @Query("select b.status from Booking b where b.id = :id")
    Optional<BookingStatus> findStatusById(@Param("id") Long id);

    Page<Booking> findByCustomerId(Long customerId, Pageable pageable);

    List<Booking> findByStatusAndLastCommandAtBefore(BookingStatus status, Instant before, Pageable pageable);

    @Query("select count(i) from BookingItem i where i.booking.id = :id and i.status = com.turfbooking.booking_service.enums.BookingItemStatus.RELEASE_PENDING")
    long countPendingReleases(@Param("id") Long id);

    // Bookings with a cancellation still waiting for turf-service's confirmation, whose release command is overdue.
    @Query("select distinct b from Booking b join b.items i where i.status = com.turfbooking.booking_service.enums.BookingItemStatus.RELEASE_PENDING and b.lastCommandAt < :before")
    List<Booking> findWithOverdueRelease(@Param("before") Instant before, Pageable pageable);

    Optional<Booking> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    // Every state change of a booking starts by locking its row, so a cancellation and a reservation reply for the
    // same booking run one after another instead of overwriting each other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}
