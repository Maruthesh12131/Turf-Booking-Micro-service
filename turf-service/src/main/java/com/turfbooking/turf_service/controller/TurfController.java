package com.turfbooking.turf_service.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turfbooking.turf_service.client.OwnerClient;
import com.turfbooking.turf_service.dto.CourtResponseDTO;
import com.turfbooking.turf_service.dto.CreateTimeSlotsRequestDTO;
import com.turfbooking.turf_service.dto.PagedResponse;
import com.turfbooking.turf_service.dto.SlotGenerationResponseDTO;
import com.turfbooking.turf_service.dto.SlotStatusUpdateDTO;
import com.turfbooking.turf_service.dto.TimeSlotResponseDTO;
import com.turfbooking.turf_service.dto.TurfDetailsDTO;
import com.turfbooking.turf_service.entity.Courts;
import com.turfbooking.turf_service.entity.Turf;
import com.turfbooking.turf_service.service.TurfService;
import com.turfbooking.turf_service.dto.TurfDTO;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/turfs")
public class TurfController {

    private final TurfService turfService;
    private final OwnerClient ownerClient;

    public TurfController(TurfService turfService, OwnerClient ownerClient) {
        this.turfService = turfService;
        this.ownerClient = ownerClient;
    }

    // The owner id always comes from owner-service for the validated token, never from the request.
    private Long ownerId(Jwt jwt) {
        return ownerClient.resolveOwnerId(jwt.getTokenValue());
    } 

    @PostMapping
    public ResponseEntity<TurfDetailsDTO> createTurf(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TurfDTO turf) {
        if(turf.getCourts() == null || turf.getCourts().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        else{
            TurfDetailsDTO savedTurf = turfService.createTurf(turf, ownerClient.resolveVerifiedOwnerId(jwt.getTokenValue()));
            return ResponseEntity.status(HttpStatus.CREATED).body(savedTurf);
        }  
    }

    // Public: browsing needs no account. Optional ?location= does a partial, case-insensitive match.
    @GetMapping
    public ResponseEntity<PagedResponse<TurfDetailsDTO>> searchTurfs(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String sport,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(turfService.searchTurfs(location, sport, page, size));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TurfDetailsDTO>> getMyTurfs(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(turfService.getTurfsByOwner(ownerId(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TurfDetailsDTO> getTurf(@PathVariable("id") Long turfId) {
        return ResponseEntity.ok(turfService.getActiveTurfDetails(turfId));
    }

    // Customers get FREE/OCCUPIED only; the owner of the turf gets the real status. Optional ?date=yyyy-MM-dd.
    @GetMapping("/{id}/timeslots")
    public ResponseEntity<?> getTimeSlots(Authentication authentication, @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long turfId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean owner = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));
        return ResponseEntity.ok(owner
                ? turfService.getTimeSlotsForOwner(turfId, ownerId(jwt), date)
                : turfService.getTimeSlotsForCustomer(turfId, date));
    }

    @PostMapping("/courts/{courtNumber}/timeslots")
    public ResponseEntity<SlotGenerationResponseDTO> createTimeSlots(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("courtNumber") Long courtNumber, @Valid @RequestBody CreateTimeSlotsRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                turfService.createTimeSlots(courtNumber, request.turfId(), request.startDate(), ownerId(jwt)));
    }

    @PutMapping("/{turfId}/timeslots/{slotId}/status")
    public ResponseEntity<TimeSlotResponseDTO> updateSlotStatus(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long turfId, @PathVariable Long slotId, @Valid @RequestBody SlotStatusUpdateDTO request) {
        return ResponseEntity.ok(turfService.updateSlotStatus(turfId, slotId, request.status(), ownerId(jwt)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TurfDetailsDTO> updateTurf(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long turfId, @Valid @RequestBody Turf turf) {
        if(turf.getCourts() == null || turf.getCourts().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        else{
            TurfDetailsDTO updatedTurf = turfService.updateTurf(turfId, turf, ownerId(jwt));
            return ResponseEntity.ok(updatedTurf);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTurf(Authentication authentication, @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long turfId) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (admin) {
            turfService.deleteTurf(turfId);
        } else {
            turfService.deleteOwnedTurf(turfId, ownerId(jwt));
        }
        return ResponseEntity.noContent().build();
    }
}
