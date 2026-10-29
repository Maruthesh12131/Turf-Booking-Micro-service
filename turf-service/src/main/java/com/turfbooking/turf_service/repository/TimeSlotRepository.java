package com.turfbooking.turf_service.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.turfbooking.turf_service.entity.TimeSlots;

import jakarta.persistence.LockModeType;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlots, Long> {

    List<TimeSlots> findByCourt_CourtIdAndSlotDateBetween(Long courtId, LocalDate from, LocalDate to);

    @Query("select s from TimeSlots s where s.court.turf.turfId = :turfId and s.slotDate >= :from "
            + "order by s.slotDate, s.court.courtNumber, s.startTime")
    List<TimeSlots> findUpcoming(@Param("turfId") Long turfId, @Param("from") LocalDate from);

    @Query("select s from TimeSlots s where s.court.turf.turfId = :turfId and s.slotDate = :date "
            + "order by s.court.courtNumber, s.startTime")
    List<TimeSlots> findOnDate(@Param("turfId") Long turfId, @Param("date") LocalDate date);

    // Every state change of a slot starts by locking its row, so concurrent reservations, blocks and
    // cancellations of the same slot run one after another and always see each other's committed result.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TimeSlots s where s.timeSlotId = :id")
    Optional<TimeSlots> findByIdForUpdate(@Param("id") Long id);

    // Locks the given slots in id order. Always locking in the same order means two bookings that overlap on several
    // slots queue up behind each other instead of deadlocking.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TimeSlots s where s.timeSlotId in :ids order by s.timeSlotId")
    List<TimeSlots> lockByIds(@Param("ids") java.util.Collection<Long> ids);

    // Locks all of a turf's slots (in id order, so concurrent callers cannot deadlock) before it is deleted.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TimeSlots s where s.court.turf.turfId = :turfId order by s.timeSlotId")
    List<TimeSlots> lockAllByTurfId(@Param("turfId") Long turfId);
}
