package com.turfbooking.turf_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.turfbooking.turf_service.entity.OwnerVisibility;
import com.turfbooking.turf_service.enums.TurfStatus;
import com.turfbooking.turf_service.event.OwnerVerificationChangedEvent;
import com.turfbooking.turf_service.repository.OwnerVisibilityRepository;
import com.turfbooking.turf_service.repository.TurfRepository;

class OwnerVisibilityServiceTest {

    OwnerVisibilityRepository visibilityRepo = mock(OwnerVisibilityRepository.class);
    TurfRepository turfRepo = mock(TurfRepository.class);
    OwnerVisibilityService service = new OwnerVisibilityService(visibilityRepo, turfRepo);

    @Test
    void rejectionHidesOwnersTurfs() {
        when(visibilityRepo.findById(5L)).thenReturn(Optional.empty());
        assertThat(service.apply(new OwnerVerificationChangedEvent(5L, "REJECTED", 1L))).isTrue();
        verify(turfRepo).updateStatusByOwner(5L, TurfStatus.HIDDEN);
        verify(visibilityRepo).save(any(OwnerVisibility.class));
    }

    @Test
    void reVerificationShowsThemAgain() {
        when(visibilityRepo.findById(5L)).thenReturn(Optional.of(new OwnerVisibility(5L, "REJECTED", 1L)));
        assertThat(service.apply(new OwnerVerificationChangedEvent(5L, "VERIFIED", 2L))).isTrue();
        verify(turfRepo).updateStatusByOwner(5L, TurfStatus.ACTIVE);
    }

    @Test
    void duplicateAndStaleEventsAreIgnored() {
        when(visibilityRepo.findById(5L)).thenReturn(Optional.of(new OwnerVisibility(5L, "VERIFIED", 3L)));
        assertThat(service.apply(new OwnerVerificationChangedEvent(5L, "VERIFIED", 3L))).isFalse();
        assertThat(service.apply(new OwnerVerificationChangedEvent(5L, "REJECTED", 2L))).isFalse();
        verify(turfRepo, never()).updateStatusByOwner(any(), any());
        verify(visibilityRepo, never()).save(any());
    }

    @Test
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> service.apply(new OwnerVerificationChangedEvent(5L, "PENDING", 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownerRevokedAMomentAgoGetsHiddenTurf() {
        when(visibilityRepo.findById(5L)).thenReturn(Optional.of(new OwnerVisibility(5L, "REJECTED", 2L)));
        assertThat(service.initialStatusFor(5L)).isEqualTo(TurfStatus.HIDDEN);
        when(visibilityRepo.findById(6L)).thenReturn(Optional.empty());
        assertThat(service.initialStatusFor(6L)).isEqualTo(TurfStatus.ACTIVE);
    }
}
