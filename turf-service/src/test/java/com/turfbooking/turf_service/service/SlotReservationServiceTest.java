package com.turfbooking.turf_service.service;

import com.turfbooking.turf_service.config.TestMessaging;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.turfbooking.turf_service.entity.Courts;
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

class SlotReservationServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T04:30:00Z"), ZoneId.of("Asia/Kolkata")); // 10:00
    static final LocalDate TOMORROW = LocalDate.of(2026, 10, 28);

    SlotReservationRepository reservations = mock(SlotReservationRepository.class);
    TimeSlotRepository slotRepo = mock(TimeSlotRepository.class);
    OutboxService outbox = mock(OutboxService.class);
    SlotReservationService service = new SlotReservationService(reservations, slotRepo, outbox, CLOCK, TestMessaging.defaults());

    Map<Long, TimeSlots> db = new HashMap<>();
    Map<Long, SlotReservation> reservationDb = new HashMap<>();
    Turf turf = new Turf();
    Courts court = new Courts();

    @BeforeEach
    void setUp() {
        turf.setTurfId(1L);
        turf.setOwnerId(7L);
        turf.setStatus(TurfStatus.ACTIVE);
        court.setCourtId(10L);
        court.setCourtNumber(2L);
        court.setTurf(turf);
        for (long id = 1; id <= 12; id++) {
            db.put(id, slot(id, TOMORROW, LocalTime.of(6, 0).plusMinutes(30 * (id - 1)), Status.FREE));
        }
        when(slotRepo.lockByIds(anyCollection())).thenAnswer(i -> {
            java.util.Collection<Long> ids = i.getArgument(0);
            return ids.stream().sorted().map(db::get).filter(java.util.Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
        });
        when(slotRepo.findAllById(any())).thenAnswer(i -> {
            Iterable<Long> ids = i.getArgument(0);
            List<TimeSlots> out = new ArrayList<>();
            ids.forEach(id -> out.add(db.get(id)));
            return out;
        });
        when(reservations.findById(any())).thenAnswer(i -> Optional.ofNullable(reservationDb.get(i.<Long>getArgument(0))));
        when(reservations.findByIdForUpdate(any())).thenAnswer(i -> Optional.ofNullable(reservationDb.get(i.<Long>getArgument(0))));
        when(reservations.save(any(SlotReservation.class))).thenAnswer(i -> {
            SlotReservation r = i.getArgument(0);
            reservationDb.put(r.getReservationId(), r);
            return r;
        });
    }

    TimeSlots slot(long id, LocalDate date, LocalTime start, Status status) {
        TimeSlots s = new TimeSlots();
        s.setTimeSlotId(id);
        s.setSlotDate(date);
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(30));
        s.setPrice(new BigDecimal("250.00"));
        s.setStatus(status);
        s.setCourt(court);
        return s;
    }

    void hold(long slotId, long reservationId, Status status) {
        db.get(slotId).setStatus(status);
        db.get(slotId).setReservationId(reservationId);
    }

    Object lastEvent(String key) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox, org.mockito.Mockito.atLeastOnce()).enqueue(anyString(), eq(key), anyString(), payload.capture());
        return payload.getAllValues().get(payload.getAllValues().size() - 1);
    }

    void assertNothingTouched() {
        assertThat(db.values()).allMatch(s -> s.getStatus() == Status.FREE && s.getReservationId() == null);
        verify(slotRepo, never()).saveAll(any());
    }

    // ---- reserve ----

    @Test
    void reservesAllRequestedSlotsAndReportsPriceSnapshots() {
        service.reserve(new ReserveSlotsCommand(500L, List.of(3L, 1L, 2L)));

        assertThat(db.get(1L).getStatus()).isEqualTo(Status.HELD);
        assertThat(List.of(db.get(1L), db.get(2L), db.get(3L))).allMatch(s -> s.getStatus() == Status.HELD && s.getReservationId() == 500L);
        assertThat(db.get(4L).getStatus()).isEqualTo(Status.FREE);
        assertThat(reservationDb.get(500L).getStatus()).isEqualTo(ReservationStatus.HELD);
        SlotsReservedEvent event = (SlotsReservedEvent) lastEvent("slots.reserved");
        assertThat(event.ownerId()).isEqualTo(7L);
        assertThat(event.turfId()).isEqualTo(1L);
        assertThat(event.courtNumber()).isEqualTo(2L);
        assertThat(event.slots()).extracting(SlotsReservedEvent.ReservedSlot::slotId).containsExactly(1L, 2L, 3L);
        assertThat(event.slots()).allMatch(s -> s.price().compareTo(new BigDecimal("250.00")) == 0);
    }

    @Test
    void anySlotNotFreeMeansNothingIsReservedAndTheAnswerIsTheSameWhateverTheReason() {
        for (Status taken : new Status[] { Status.HELD, Status.BOOKED, Status.BLOCKED }) {
            db.values().forEach(s -> { s.setStatus(Status.FREE); s.setReservationId(null); });
            reservationDb.clear();
            if (taken == Status.BLOCKED) { db.get(2L).setStatus(Status.BLOCKED); } else { hold(2L, 99L, taken); }

            service.reserve(new ReserveSlotsCommand(501L, List.of(1L, 2L)));

            SlotsRejectedEvent rejected = (SlotsRejectedEvent) lastEvent("slots.rejected");
            assertThat(rejected.code()).isEqualTo("UNAVAILABLE");
            assertThat(rejected.message()).isEqualTo("One or more of the selected slots are no longer available");
            assertThat(db.get(1L).getStatus()).isEqualTo(Status.FREE); // all-or-nothing
            assertThat(reservationDb.get(501L).getStatus()).isEqualTo(ReservationStatus.REJECTED);
        }
    }

    @Test
    void slotsThatAlreadyStartedOrBelongToAHiddenTurfAreUnavailable() {
        db.put(20L, slot(20L, LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), Status.FREE)); // today 09:00, clock is 10:00
        service.reserve(new ReserveSlotsCommand(502L, List.of(20L)));
        assertThat(((SlotsRejectedEvent) lastEvent("slots.rejected")).code()).isEqualTo("UNAVAILABLE");

        turf.setStatus(TurfStatus.HIDDEN);
        service.reserve(new ReserveSlotsCommand(503L, List.of(1L)));
        assertThat(reservationDb.get(503L).getRejectCode()).isEqualTo("UNAVAILABLE");
        assertThat(db.get(1L).getStatus()).isEqualTo(Status.FREE);
    }

    @Test
    void invalidRequestsAreRejectedWithoutTouchingSlots() {
        service.reserve(new ReserveSlotsCommand(510L, List.of()));
        service.reserve(new ReserveSlotsCommand(511L, java.util.stream.LongStream.rangeClosed(1, 11).boxed().toList()));
        service.reserve(new ReserveSlotsCommand(512L, List.of(1L, 1L)));
        service.reserve(new ReserveSlotsCommand(513L, List.of(1L, 999L)));
        assertThat(List.of(510L, 511L, 512L, 513L)).allMatch(id -> reservationDb.get(id).getRejectCode().equals("INVALID"));
        assertNothingTouched();
    }

    @Test
    void slotsOnDifferentCourtsOrDatesAreRejected() {
        Courts other = new Courts();
        other.setCourtId(11L);
        other.setCourtNumber(3L);
        other.setTurf(turf);
        db.get(2L).setCourt(other);
        service.reserve(new ReserveSlotsCommand(520L, List.of(1L, 2L)));
        db.get(2L).setCourt(court);
        db.get(2L).setSlotDate(TOMORROW.plusDays(1));
        service.reserve(new ReserveSlotsCommand(521L, List.of(1L, 2L)));
        assertThat(reservationDb.get(520L).getRejectCode()).isEqualTo("INVALID");
        assertThat(reservationDb.get(521L).getRejectCode()).isEqualTo("INVALID");
        assertNothingTouched();
    }

    // ---- idempotency ----

    @Test
    void sameReserveTwiceTakesSlotsOnceAndRepeatsTheReply() {
        service.reserve(new ReserveSlotsCommand(600L, List.of(1L, 2L)));
        service.reserve(new ReserveSlotsCommand(600L, List.of(1L, 2L)));

        verify(slotRepo, org.mockito.Mockito.times(1)).saveAll(any());
        verify(outbox, org.mockito.Mockito.times(2)).enqueue(anyString(), eq("slots.reserved"), eq("600"), any());
    }

    @Test
    void replayAfterAnotherBookingTookTheSlotsStillAnswersForTheOriginalReservation() {
        service.reserve(new ReserveSlotsCommand(601L, List.of(1L)));
        service.reserve(new ReserveSlotsCommand(602L, List.of(1L))); // loses: slot is held by 601
        assertThat(reservationDb.get(602L).getStatus()).isEqualTo(ReservationStatus.REJECTED);
        service.reserve(new ReserveSlotsCommand(602L, List.of(1L))); // redelivered: same rejection, not a retry of the hold
        assertThat(db.get(1L).getReservationId()).isEqualTo(601L);
        verify(outbox, org.mockito.Mockito.times(2)).enqueue(anyString(), eq("slots.rejected"), eq("602"), any());
    }

    // ---- confirm ----

    @Test
    void confirmTurnsHeldSlotsIntoBookedAndIsRepeatable() {
        service.reserve(new ReserveSlotsCommand(700L, List.of(1L, 2L)));
        service.confirm(new ConfirmReservationCommand(700L));
        service.confirm(new ConfirmReservationCommand(700L));

        assertThat(List.of(db.get(1L), db.get(2L))).allMatch(s -> s.getStatus() == Status.BOOKED && s.getReservationId() == 700L);
        assertThat(reservationDb.get(700L).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void confirmOfUnknownRejectedOrBrokenReservationNeedsAHuman() {
        assertThatThrownBy(() -> service.confirm(new ConfirmReservationCommand(799L))).isInstanceOf(ReservationStateException.class);
        service.reserve(new ReserveSlotsCommand(797L, List.of()));
        assertThatThrownBy(() -> service.confirm(new ConfirmReservationCommand(797L))).isInstanceOf(ReservationStateException.class);
        service.reserve(new ReserveSlotsCommand(796L, List.of(5L)));
        db.get(5L).setReservationId(12345L); // someone else has it: invariant broken
        assertThatThrownBy(() -> service.confirm(new ConfirmReservationCommand(796L))).isInstanceOf(ReservationStateException.class);
    }

    @Test
    void staleConfirmAfterReleaseIsIgnoredAndDoesNotRebookTheSlots() {
        service.reserve(new ReserveSlotsCommand(795L, List.of(1L)));
        service.confirm(new ConfirmReservationCommand(795L));
        service.release(new ReleaseSlotsCommand(795L, null));
        service.confirm(new ConfirmReservationCommand(795L)); // duplicate delivered late
        service.release(new ReleaseSlotsCommand(794L, null)); // tombstone
        service.confirm(new ConfirmReservationCommand(794L));

        assertThat(db.get(1L).getStatus()).isEqualTo(Status.FREE);
        assertThat(reservationDb.get(795L).getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    // ---- release ----

    @Test
    void releaseBeforeConfirmFreesTheHeldSlots() {
        service.reserve(new ReserveSlotsCommand(800L, List.of(1L, 2L)));
        service.release(new ReleaseSlotsCommand(800L, null));

        assertThat(List.of(db.get(1L), db.get(2L))).allMatch(s -> s.getStatus() == Status.FREE && s.getReservationId() == null);
        assertThat(reservationDb.get(800L).getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(((SlotsReleasedEvent) lastEvent("slots.released")).slotIds()).containsExactly(1L, 2L);
    }

    @Test
    void releaseAfterConfirmFreesBookedSlotsAndPartialReleaseKeepsTheRest() {
        service.reserve(new ReserveSlotsCommand(801L, List.of(1L, 2L, 3L)));
        service.confirm(new ConfirmReservationCommand(801L));
        service.release(new ReleaseSlotsCommand(801L, List.of(2L)));

        assertThat(db.get(2L).getStatus()).isEqualTo(Status.FREE);
        assertThat(db.get(1L).getStatus()).isEqualTo(Status.BOOKED);
        assertThat(db.get(3L).getStatus()).isEqualTo(Status.BOOKED);
        assertThat(reservationDb.get(801L).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        service.release(new ReleaseSlotsCommand(801L, List.of(1L, 3L)));
        assertThat(reservationDb.get(801L).getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(db.values()).allMatch(s -> s.getStatus() == Status.FREE);
    }

    @Test
    void releaseNeverFreesASlotThatSomeoneElseNowHolds() {
        service.reserve(new ReserveSlotsCommand(802L, List.of(1L)));
        service.release(new ReleaseSlotsCommand(802L, null));
        service.reserve(new ReserveSlotsCommand(803L, List.of(1L))); // another booking takes the freed slot
        service.release(new ReleaseSlotsCommand(802L, null));          // duplicate/late release of the first
        service.release(new ReleaseSlotsCommand(802L, List.of(1L)));

        assertThat(db.get(1L).getStatus()).isEqualTo(Status.HELD);
        assertThat(db.get(1L).getReservationId()).isEqualTo(803L);
    }

    @Test
    void releaseIgnoresSlotIdsThatAreNotPartOfTheReservation() {
        service.reserve(new ReserveSlotsCommand(804L, List.of(1L)));
        hold(5L, 999L, Status.BOOKED); // belongs to a different booking
        service.release(new ReleaseSlotsCommand(804L, List.of(1L, 5L)));
        assertThat(db.get(5L).getStatus()).isEqualTo(Status.BOOKED);
        assertThat(db.get(5L).getReservationId()).isEqualTo(999L);
        assertThat(db.get(1L).getStatus()).isEqualTo(Status.FREE);
    }

    @Test
    void releaseArrivingBeforeReserveLeavesATombstoneThatRefusesTheLateReserve() {
        service.release(new ReleaseSlotsCommand(900L, null));
        assertThat(reservationDb.get(900L).getStatus()).isEqualTo(ReservationStatus.RELEASED);

        service.reserve(new ReserveSlotsCommand(900L, List.of(1L, 2L)));

        assertNothingTouched();
        verify(outbox, org.mockito.Mockito.times(2)).enqueue(anyString(), eq("slots.released"), eq("900"), any());
        verify(outbox, never()).enqueue(anyString(), eq("slots.reserved"), anyString(), any());
    }

    @Test
    void releasingARejectedReservationChangesNothing() {
        service.reserve(new ReserveSlotsCommand(901L, List.of()));
        service.release(new ReleaseSlotsCommand(901L, null));
        assertThat(reservationDb.get(901L).getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertNothingTouched();
    }
}
