package com.turfbooking.turf_service.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turfbooking.turf_service.config.MessagingProperties;
import com.turfbooking.turf_service.entity.SlotReservation;
import com.turfbooking.turf_service.entity.TimeSlots;
import com.turfbooking.turf_service.entity.Turf;
import com.turfbooking.turf_service.enums.ReservationStatus;
import com.turfbooking.turf_service.enums.Status;
import com.turfbooking.turf_service.enums.TurfStatus;
import com.turfbooking.turf_service.event.ConfirmReservationCommand;
import com.turfbooking.turf_service.event.ReleaseSlotsCommand;
import com.turfbooking.turf_service.event.ReserveSlotsCommand;
import com.turfbooking.turf_service.event.SlotsRejectedEvent;
import com.turfbooking.turf_service.event.SlotsReleasedEvent;
import com.turfbooking.turf_service.event.SlotsReservedEvent;
import com.turfbooking.turf_service.exception.ReservationStateException;
import com.turfbooking.turf_service.outbox.OutboxService;
import com.turfbooking.turf_service.repository.SlotReservationRepository;
import com.turfbooking.turf_service.repository.TimeSlotRepository;

/**
 * Turf-service's side of a booking. Every method is one database transaction that locks the slots (in id order),
 * changes them, records the outcome in slot_reservations and queues the reply in the outbox. So a reply exists if and
 * only if the slot changes were committed, and handling the same command again just repeats the stored outcome.
 */
@Service
public class SlotReservationService {

    private static final Logger log = LoggerFactory.getLogger(SlotReservationService.class);

    static final int MAX_SLOTS = 10;
    static final String UNAVAILABLE = "UNAVAILABLE";
    static final String INVALID = "INVALID";

    private final SlotReservationRepository reservations;
    private final TimeSlotRepository slots;
    private final OutboxService outbox;
    private final Clock clock;
    private final MessagingProperties messaging;

    public SlotReservationService(SlotReservationRepository reservations, TimeSlotRepository slots,
            OutboxService outbox, Clock clock, MessagingProperties messaging) {
        this.reservations = reservations;
        this.slots = slots;
        this.outbox = outbox;
        this.clock = clock;
        this.messaging = messaging;
    }

    /** All the requested slots become HELD for this reservation, or none of them do. */
    @Transactional
    public void reserve(ReserveSlotsCommand command) {
        Long id = command.reservationId();
        SlotReservation existing = reservations.findById(id).orElse(null);
        if (existing != null) {
            replay(existing);
            return;
        }
        List<Long> ids = command.slotIds() == null ? List.of() : new ArrayList<>(new TreeSet<>(command.slotIds()));
        if (ids.isEmpty() || ids.size() > MAX_SLOTS || ids.size() != command.slotIds().size()) {
            reject(id, INVALID, "Choose between 1 and " + MAX_SLOTS + " different slots");
            return;
        }
        List<TimeSlots> locked = slots.lockByIds(ids);
        if (locked.size() != ids.size()) {
            reject(id, INVALID, "One or more of the selected slots do not exist");
            return;
        }
        TimeSlots first = locked.get(0);
        boolean sameCourtAndDate = locked.stream().allMatch(s ->
                s.getCourt().getCourtId().equals(first.getCourt().getCourtId()) && s.getSlotDate().equals(first.getSlotDate()));
        if (!sameCourtAndDate) {
            reject(id, INVALID, "All slots in a booking must be on the same court and the same date");
            return;
        }
        // The same answer whatever the reason (taken, held, blocked by the owner, already started, turf hidden):
        // a customer must not learn why a slot is out of reach.
        LocalDateTime now = LocalDateTime.now(clock);
        boolean allFree = first.getCourt().getTurf().getStatus() == TurfStatus.ACTIVE && locked.stream().allMatch(s ->
                s.getStatus() == Status.FREE && LocalDateTime.of(s.getSlotDate(), s.getStartTime()).isAfter(now));
        if (!allFree) {
            reject(id, UNAVAILABLE, "One or more of the selected slots are no longer available");
            return;
        }
        for (TimeSlots slot : locked) {
            slot.setStatus(Status.HELD);
            slot.setReservationId(id);
        }
        slots.saveAll(locked);
        SlotReservation reservation = new SlotReservation(id, ReservationStatus.HELD);
        reservation.setSlotIdList(ids);
        reservations.save(reservation);
        send(messaging.reservedKey(), id, reservedEvent(id, locked));
    }

    /** HELD slots become BOOKED. Repeating it changes nothing. */
    @Transactional
    public void confirm(ConfirmReservationCommand command) {
        Long id = command.reservationId();
        SlotReservation reservation = reservations.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationStateException("Confirm for unknown reservation " + id));
        switch (reservation.getStatus()) {
            case CONFIRMED -> { /* already done */ }
            // A confirm is only ever sent before any release of the same booking, so one arriving after the slots were
            // given back is a stale duplicate. Ignoring it is correct; re-booking the slots would not be.
            case RELEASED -> log.warn("Ignoring stale confirm for released reservation {}", id);
            case HELD -> {
                List<TimeSlots> locked = slots.lockByIds(reservation.slotIdList());
                for (TimeSlots slot : locked) {
                    if (!id.equals(slot.getReservationId()) || slot.getStatus() != Status.HELD) {
                        throw new ReservationStateException("Slot " + slot.getTimeSlotId() + " is not held by reservation " + id);
                    }
                    slot.setStatus(Status.BOOKED);
                }
                slots.saveAll(locked);
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservations.save(reservation);
            }
            default -> throw new ReservationStateException(
                    "Cannot confirm reservation " + id + " in state " + reservation.getStatus());
        }
    }

    /**
     * Gives slots back. Only slots that still belong to this reservation are freed, so a late or repeated release can
     * never free a slot that someone else has since taken. If the reservation was never seen, a RELEASED marker is
     * stored so that a reserve arriving afterwards is refused.
     */
    @Transactional
    public void release(ReleaseSlotsCommand command) {
        Long id = command.reservationId();
        SlotReservation reservation = reservations.findByIdForUpdate(id).orElse(null);
        if (reservation == null) {
            reservations.save(new SlotReservation(id, ReservationStatus.RELEASED));
            send(messaging.releasedKey(), id, new SlotsReleasedEvent(id, List.of()));
            return;
        }
        if (reservation.getStatus() == ReservationStatus.REJECTED || reservation.getStatus() == ReservationStatus.RELEASED) {
            send(messaging.releasedKey(), id, new SlotsReleasedEvent(id, List.of()));
            return;
        }
        Set<Long> owned = new HashSet<>(reservation.slotIdList());
        List<Long> target = command.slotIds() == null ? new ArrayList<>(new TreeSet<>(owned))
                : command.slotIds().stream().filter(owned::contains).distinct().sorted().toList();
        if (!target.isEmpty()) {
            for (TimeSlots slot : slots.lockByIds(target)) {
                if (id.equals(slot.getReservationId())) {
                    slot.setStatus(Status.FREE);
                    slot.setReservationId(null);
                    slots.save(slot);
                }
            }
        }
        boolean anyLeft = slots.lockByIds(reservation.slotIdList()).stream().anyMatch(s -> id.equals(s.getReservationId()));
        if (!anyLeft) {
            reservation.setStatus(ReservationStatus.RELEASED);
            reservations.save(reservation);
        }
        send(messaging.releasedKey(), id, new SlotsReleasedEvent(id, target));
    }

    private void reject(Long id, String code, String message) {
        SlotReservation reservation = new SlotReservation(id, ReservationStatus.REJECTED);
        reservation.setRejectCode(code);
        reservation.setRejectMessage(message);
        reservations.save(reservation);
        send(messaging.rejectedKey(), id, new SlotsRejectedEvent(id, code, message));
    }

    // Same command again: say what happened the first time, touch no slot.
    private void replay(SlotReservation reservation) {
        Long id = reservation.getReservationId();
        switch (reservation.getStatus()) {
            case HELD, CONFIRMED -> send(messaging.reservedKey(), id, reservedEvent(id, slots.findAllById(reservation.slotIdList())));
            case REJECTED -> send(messaging.rejectedKey(), id,
                    new SlotsRejectedEvent(id, reservation.getRejectCode(), reservation.getRejectMessage()));
            case RELEASED -> send(messaging.releasedKey(), id, new SlotsReleasedEvent(id, reservation.slotIdList()));
        }
    }

    private static SlotsReservedEvent reservedEvent(Long id, Collection<TimeSlots> held) {
        List<TimeSlots> ordered = held.stream().sorted((a, b) -> a.getTimeSlotId().compareTo(b.getTimeSlotId())).toList();
        TimeSlots first = ordered.get(0);
        Turf turf = first.getCourt().getTurf();
        return new SlotsReservedEvent(id, turf.getTurfId(), turf.getOwnerId(), first.getCourt().getCourtNumber(),
                first.getSlotDate(),
                ordered.stream().map(s -> new SlotsReservedEvent.ReservedSlot(s.getTimeSlotId(), s.getSlotDate(),
                        s.getStartTime(), s.getEndTime(), s.getPrice())).toList());
    }

    private void send(String routingKey, Long reservationId, Object payload) {
        outbox.enqueue(messaging.turfEventsExchange(), routingKey, String.valueOf(reservationId), payload);
    }
}
