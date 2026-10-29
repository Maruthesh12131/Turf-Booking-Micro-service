package com.turfbooking.owner_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import com.turfbooking.owner_service.config.MessagingProperties;
import com.turfbooking.owner_service.dto.OwnerRequestDTO;
import com.turfbooking.owner_service.dto.VerificationUpdateDTO;
import com.turfbooking.owner_service.event.OwnerVerificationChangedEvent;
import com.turfbooking.owner_service.exception.InvalidVerificationRequestException;
import com.turfbooking.owner_service.exception.InvalidVerificationTransitionException;
import com.turfbooking.owner_service.outbox.OutboxService;
import com.turfbooking.owner_service.dto.OwnerResponseDTO;
import com.turfbooking.owner_service.entity.Owner;
import com.turfbooking.owner_service.enums.VerificationStatus;
import com.turfbooking.owner_service.exception.OwnerAlreadyExistsException;
import com.turfbooking.owner_service.exception.OwnerNotFoundException;
import com.turfbooking.owner_service.repository.OwnerRepository;

@Service
public class OwnerService {

    // An admin can approve or reject a new owner, revoke an approved one, and re-approve a rejected one.
    private static final Map<VerificationStatus, Set<VerificationStatus>> TRANSITIONS = Map.of(
            VerificationStatus.PENDING, Set.of(VerificationStatus.VERIFIED, VerificationStatus.REJECTED),
            VerificationStatus.VERIFIED, Set.of(VerificationStatus.REJECTED),
            VerificationStatus.REJECTED, Set.of(VerificationStatus.VERIFIED));

    private final OwnerRepository ownerRepository;
    private final OutboxService outboxService;
    private final MessagingProperties messaging;

    public OwnerService(OwnerRepository ownerRepository, OutboxService outboxService, MessagingProperties messaging) {
        this.messaging = messaging;
        this.ownerRepository = ownerRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public OwnerResponseDTO createOwner(Long userId, OwnerRequestDTO request) {
        if (ownerRepository.existsByUserId(userId)) {
            throw new OwnerAlreadyExistsException("Owner profile already exists for this account");
        }
        Owner owner = new Owner();
        owner.setUserId(userId);
        owner.setBusinessName(request.getBusinessName().trim());
        owner.setPhoneNumber(request.getPhoneNumber());
        owner.setVerificationStatus(VerificationStatus.PENDING);
        return toResponse(ownerRepository.save(owner));
    }

    /** Creates the profile unless one exists for this account; returns whether it created one. */
    @Transactional
    public boolean createIfAbsent(Long userId, OwnerRequestDTO request) {
        if (ownerRepository.existsByUserId(userId)) {
            return false;
        }
        createOwner(userId, request);
        return true;
    }

    public OwnerResponseDTO getByUserId(Long userId) {
        return toResponse(findByUserId(userId));
    }

    @Transactional
    public OwnerResponseDTO updateOwner(Long userId, OwnerRequestDTO request) {
        Owner owner = findByUserId(userId);
        owner.setBusinessName(request.getBusinessName().trim());
        owner.setPhoneNumber(request.getPhoneNumber());
        return toResponse(ownerRepository.save(owner));
    }

    /**
     * Admin decision. The status change and the event telling turf-service to show or hide the owner's turfs are
     * committed together (outbox), so one cannot happen without the other.
     */
    @Transactional
    public OwnerResponseDTO updateVerification(Long ownerId, VerificationUpdateDTO request, Long adminUserId) {
        VerificationStatus target = request.getStatus();
        if (target == VerificationStatus.PENDING) {
            throw new InvalidVerificationRequestException("status must be VERIFIED or REJECTED");
        }
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (target == VerificationStatus.REJECTED && (reason == null || reason.isEmpty())) {
            throw new InvalidVerificationRequestException("A reason is required when rejecting an owner");
        }
        Owner owner = ownerRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new OwnerNotFoundException("Owner not found"));
        VerificationStatus current = owner.getVerificationStatus();
        if (!TRANSITIONS.get(current).contains(target)) {
            throw new InvalidVerificationTransitionException(
                    "Owner is already " + current + "; cannot change " + current + " to " + target);
        }
        owner.setVerificationStatus(target);
        owner.setVerificationReason(reason == null || reason.isEmpty() ? null : reason);
        owner.setVerifiedBy(adminUserId);
        owner.setVerificationUpdatedAt(Instant.now());
        owner.setVerificationVersion(owner.getVerificationVersion() + 1);
        Owner saved = ownerRepository.save(owner);
        outboxService.enqueue(messaging.ownerEventsExchange(), messaging.verificationChangedKey(),
                new OwnerVerificationChangedEvent(saved.getOwnerId(), target.name(), saved.getVerificationVersion()));
        return toResponse(saved);
    }

    private Owner findByUserId(Long userId) {
        return ownerRepository.findByUserId(userId)
                .orElseThrow(() -> new OwnerNotFoundException("Owner profile not found"));
    }

    private static OwnerResponseDTO toResponse(Owner owner) {
        return new OwnerResponseDTO(
                owner.getOwnerId(),
                owner.getBusinessName(),
                owner.getPhoneNumber(),
                owner.getVerificationStatus(),
                owner.getCreatedAt());
    }
}
