package com.turfbooking.owner_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.turfbooking.owner_service.config.TestMessaging;
import com.turfbooking.owner_service.dto.OwnerRequestDTO;
import com.turfbooking.owner_service.dto.VerificationUpdateDTO;
import com.turfbooking.owner_service.event.OwnerVerificationChangedEvent;
import com.turfbooking.owner_service.exception.InvalidVerificationRequestException;
import com.turfbooking.owner_service.exception.InvalidVerificationTransitionException;
import com.turfbooking.owner_service.dto.OwnerResponseDTO;
import com.turfbooking.owner_service.entity.Owner;
import com.turfbooking.owner_service.enums.VerificationStatus;
import com.turfbooking.owner_service.exception.OwnerAlreadyExistsException;
import com.turfbooking.owner_service.exception.OwnerNotFoundException;
import com.turfbooking.owner_service.repository.OwnerRepository;
import com.turfbooking.owner_service.outbox.OutboxService;

class OwnerServiceTest {

    OwnerRepository repo = mock(OwnerRepository.class);
    OutboxService outbox = mock(OutboxService.class);
    OwnerService service = new OwnerService(repo, outbox, TestMessaging.defaults());
    OwnerRequestDTO request = new OwnerRequestDTO("  Arena Co ", "+919876543210");

    @Test
    void createsPendingProfileForAccount() {
        when(repo.existsByUserId(42L)).thenReturn(false);
        when(repo.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));

        OwnerResponseDTO created = service.createOwner(42L, request);

        assertThat(created.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(created.getBusinessName()).isEqualTo("Arena Co");
    }

    @Test
    void secondProfileForSameAccountIsRejected() {
        when(repo.existsByUserId(42L)).thenReturn(true);
        assertThatThrownBy(() -> service.createOwner(42L, request)).isInstanceOf(OwnerAlreadyExistsException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void lookupIsByUserIdAndMissingIsNotFound() {
        when(repo.findByUserId(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByUserId(42L)).isInstanceOf(OwnerNotFoundException.class);
    }

    @Test
    void updateCannotChangeVerificationStatus() {
        Owner existing = new Owner();
        existing.setUserId(42L);
        existing.setVerificationStatus(VerificationStatus.REJECTED);
        when(repo.findByUserId(42L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));

        OwnerResponseDTO updated = service.updateOwner(42L, request);

        assertThat(updated.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    }

    private Owner ownerWithStatus(VerificationStatus status) {
        Owner o = new Owner();
        o.setOwnerId(5L);
        o.setVerificationStatus(status);
        o.setVerificationVersion(2);
        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(o));
        when(repo.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));
        return o;
    }

    @Test
    void adminCanVerifyPendingOwnerAndEventIsQueuedWithNextVersion() {
        Owner o = ownerWithStatus(VerificationStatus.PENDING);

        OwnerResponseDTO result = service.updateVerification(5L, new VerificationUpdateDTO(VerificationStatus.VERIFIED, null), 99L);

        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(o.getVerifiedBy()).isEqualTo(99L);
        verify(outbox).enqueue("owner.events", "owner.verification.changed",
                new OwnerVerificationChangedEvent(5L, "VERIFIED", 3L));
    }

    @Test
    void allowedTransitionsSucceed() {
        for (VerificationStatus[] t : new VerificationStatus[][] {
                { VerificationStatus.PENDING, VerificationStatus.REJECTED },
                { VerificationStatus.VERIFIED, VerificationStatus.REJECTED },
                { VerificationStatus.REJECTED, VerificationStatus.VERIFIED } }) {
            ownerWithStatus(t[0]);
            assertThat(service.updateVerification(5L, new VerificationUpdateDTO(t[1], "because"), 1L).getVerificationStatus())
                    .isEqualTo(t[1]);
        }
    }

    @Test
    void sameStatusIsConflictAndQueuesNothing() {
        ownerWithStatus(VerificationStatus.VERIFIED);
        assertThatThrownBy(() -> service.updateVerification(5L, new VerificationUpdateDTO(VerificationStatus.VERIFIED, null), 1L))
                .isInstanceOf(InvalidVerificationTransitionException.class);
        verify(outbox, never()).enqueue(any(), any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void pendingCannotBeSetAndRejectionNeedsReason() {
        ownerWithStatus(VerificationStatus.VERIFIED);
        assertThatThrownBy(() -> service.updateVerification(5L, new VerificationUpdateDTO(VerificationStatus.PENDING, null), 1L))
                .isInstanceOf(InvalidVerificationRequestException.class);
        assertThatThrownBy(() -> service.updateVerification(5L, new VerificationUpdateDTO(VerificationStatus.REJECTED, "  "), 1L))
                .isInstanceOf(InvalidVerificationRequestException.class);
        verify(outbox, never()).enqueue(any(), any(), any());
    }

    @Test
    void unknownOwnerIsNotFound() {
        when(repo.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateVerification(404L, new VerificationUpdateDTO(VerificationStatus.VERIFIED, null), 1L))
                .isInstanceOf(OwnerNotFoundException.class);
    }
}
