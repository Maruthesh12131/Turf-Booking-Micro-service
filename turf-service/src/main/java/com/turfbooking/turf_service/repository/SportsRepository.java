package com.turfbooking.turf_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.turfbooking.turf_service.entity.Sports;

@Repository
public interface SportsRepository extends JpaRepository<Sports, Long> {

    Optional<Sports> findBySportNameIgnoreCase(String sportName);

    List<Sports> findAllByOrderBySportNameAsc();
}
