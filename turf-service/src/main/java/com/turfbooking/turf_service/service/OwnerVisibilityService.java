package com.turfbooking.turf_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turfbooking.turf_service.entity.OwnerVisibility;
import com.turfbooking.turf_service.enums.TurfStatus;
import com.turfbooking.turf_service.event.OwnerVerificationChangedEvent;
import com.turfbooking.turf_service.repository.OwnerVisibilityRepository;
import com.turfbooking.turf_service.repository.TurfRepository;

@Service
public class OwnerVisibilityService {

    private static final Logger log = LoggerFactory.getLogger(OwnerVisibilityService.class);

    private final OwnerVisibilityRepository visibilityRepository;
    private final TurfRepository turfRepository;

    public OwnerVisibilityService(OwnerVisibilityRepository visibilityRepository, TurfRepository turfRepository) {
        this.visibilityRepository = visibilityRepository;
        this.turfRepository = turfRepository;
    }

    /**
     * Shows a VERIFIED owner's turfs and hides a REJECTED owner's. Idempotent and order-safe: an event whose version
     * is not newer than the last applied one is ignored, so redelivery and reordering cannot undo a later decision.
     * Existing bookings are not touched.
     *
     * @return true if the event changed anything
     */
    @Transactional
    public boolean apply(OwnerVerificationChangedEvent event) {
        TurfStatus target = switch (event.status()) {
            case "VERIFIED" -> TurfStatus.ACTIVE;
            case "REJECTED" -> TurfStatus.HIDDEN;
            default -> throw new IllegalArgumentException("Unsupported verification status: " + event.status());
        };
        OwnerVisibility known = visibilityRepository.findById(event.ownerId()).orElse(null);
        if (known != null && known.getVersion() >= event.version()) {
            log.info("Ignoring stale verification event for ownerId={} (version {} <= {})",
                    event.ownerId(), event.version(), known.getVersion());
            return false;
        }
        visibilityRepository.save(new OwnerVisibility(event.ownerId(), event.status(), event.version()));
        int changed = turfRepository.updateStatusByOwner(event.ownerId(), target);
        log.info("Owner {} is now {}: {} turf(s) set to {}", event.ownerId(), event.status(), changed, target);
        return true;
    }

    /** Status a turf created now by this owner should start with (an owner revoked a moment ago must not get an ACTIVE turf). */
    @Transactional(readOnly = true)
    public TurfStatus initialStatusFor(Long ownerId) {
        return visibilityRepository.findById(ownerId)
                .filter(v -> "REJECTED".equals(v.getVerificationStatus()))
                .map(v -> TurfStatus.HIDDEN)
                .orElse(TurfStatus.ACTIVE);
    }
}
