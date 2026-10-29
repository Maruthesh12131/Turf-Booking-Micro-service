package com.turfbooking.owner_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.turfbooking.owner_service.entity.Owner;

@Repository
public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByUserId(Long userId);

    // Serializes concurrent admin decisions on the same owner so the transition check cannot race.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Owner o where o.ownerId = :ownerId")
    Optional<Owner> findByIdForUpdate(@Param("ownerId") Long ownerId);

    boolean existsByUserId(Long userId);
}
