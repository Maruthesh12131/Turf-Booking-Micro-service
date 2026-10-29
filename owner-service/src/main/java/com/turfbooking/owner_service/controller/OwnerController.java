package com.turfbooking.owner_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turfbooking.owner_service.dto.OwnerRequestDTO;
import com.turfbooking.owner_service.dto.OwnerResponseDTO;
import com.turfbooking.owner_service.dto.VerificationUpdateDTO;
import com.turfbooking.owner_service.service.OwnerService;

import jakarta.validation.Valid;

// Every endpoint acts on the profile of the authenticated account (JWT "uid" claim); no owner or user id is accepted from the client.
@RestController
@RequestMapping("/owners")
public class OwnerController {

    private final OwnerService ownerService;

    public OwnerController(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

    @PostMapping
    public ResponseEntity<OwnerResponseDTO> createOwner(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OwnerRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ownerService.createOwner(userId(jwt), request));
    }

    @GetMapping("/me")
    public ResponseEntity<OwnerResponseDTO> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ownerService.getByUserId(userId(jwt)));
    }

    @PutMapping("/me")
    public ResponseEntity<OwnerResponseDTO> updateMyProfile(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OwnerRequestDTO request) {
        return ResponseEntity.ok(ownerService.updateOwner(userId(jwt), request));
    }

    // Admin only (see SecurityConfig). The admin's own id is recorded from the token, never taken from the body.
    @PutMapping("/{ownerId}/verification")
    public ResponseEntity<OwnerResponseDTO> updateVerification(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long ownerId, @Valid @RequestBody VerificationUpdateDTO request) {
        return ResponseEntity.ok(ownerService.updateVerification(ownerId, request, userId(jwt)));
    }

    private static Long userId(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        if (!(uid instanceof Number)) {
            // Token predates the uid claim; force a fresh login rather than guess an identity.
            throw new org.springframework.security.access.AccessDeniedException("Token has no user id");
        }
        return ((Number) uid).longValue();
    }
}
