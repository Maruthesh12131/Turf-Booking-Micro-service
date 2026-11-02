package com.turfbooking.turf_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.turfbooking.turf_service.dto.CustomerTimeSlotDTO;
import com.turfbooking.turf_service.dto.TimeSlotResponseDTO;
import com.turfbooking.turf_service.dto.TurfDTO;
import com.turfbooking.turf_service.enums.CustomerSlotStatus;
import com.turfbooking.turf_service.exception.SlotsAlreadyExistException;
import com.turfbooking.turf_service.entity.Courts;
import com.turfbooking.turf_service.entity.Sports;
import com.turfbooking.turf_service.repository.SportsRepository;
import com.turfbooking.turf_service.entity.TimeSlots;
import com.turfbooking.turf_service.entity.Turf;
import com.turfbooking.turf_service.enums.Status;
import com.turfbooking.turf_service.enums.TurfStatus;
import com.turfbooking.turf_service.exception.SlotsUnavailableException;
import com.turfbooking.turf_service.exception.TurfAccessDeniedException;
import com.turfbooking.turf_service.exception.TurfNotFoundException;
import com.turfbooking.turf_service.repository.CourtRepository;
import com.turfbooking.turf_service.repository.TimeSlotRepository;
import com.turfbooking.turf_service.repository.TurfRepository;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

class TurfServiceTest {

    private static final Long OWNER = 7L;
    private static final Long OTHER_OWNER = 8L;

    TurfRepository turfRepository = mock(TurfRepository.class);
    CourtRepository courtRepository = mock(CourtRepository.class);
    TimeSlotRepository timeSlotRepository = mock(TimeSlotRepository.class);
    OwnerVisibilityService visibility = mock(OwnerVisibilityService.class);
    SportsRepository sportsRepository = mock(SportsRepository.class);
    // 2026-10-27 10:00 at the turf.
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
    long nextSportId = 100;
    TurfService service;

    Turf turf;
    Courts court;

    @BeforeEach
    void setUp() {
        service = new TurfService(turfRepository, courtRepository, timeSlotRepository, visibility, sportsRepository, CLOCK);
        turf = new Turf();
        turf.setTurfId(1L);
        turf.setOwnerId(OWNER);
        turf.setName("Arena");
        turf.setLocation("City");
        turf.setOpeningTime(LocalTime.of(6, 0));
        turf.setClosingTime(LocalTime.of(7, 0));
        court = new Courts();
        court.setCourtId(10L);
        court.setCourtNumber(1L);
        court.setAmountPerHour(500L);
        court.setTurf(turf);
        turf.setCourts(List.of(court));
        when(visibility.initialStatusFor(any())).thenReturn(TurfStatus.ACTIVE);
        when(sportsRepository.findBySportNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(sportsRepository.save(any(Sports.class))).thenAnswer(i -> {
            Sports sp = i.getArgument(0);
            if (sp.getSportId() == null) { sp.setSportId(++nextSportId); }
            return sp;
        });
        when(turfRepository.findById(1L)).thenReturn(Optional.of(turf));
        when(courtRepository.findByTurf_TurfId(1L)).thenReturn(List.of(court));
    }

    @Test
    void createTurfStampsOwnerAndIgnoresClientSuppliedCourtId() {
        Courts incoming = new Courts();
        incoming.setCourtId(999L); // would overwrite another owner's court if saved as-is
        incoming.setAmountPerHour(300L);
        TurfDTO dto = new TurfDTO("New", "Town", null, List.of(incoming), LocalTime.of(6, 0), LocalTime.of(22, 0));

        service.createTurf(dto, OWNER);

        ArgumentCaptor<Turf> savedTurf = ArgumentCaptor.forClass(Turf.class);
        verify(turfRepository).save(savedTurf.capture());
        assertThat(savedTurf.getValue().getOwnerId()).isEqualTo(OWNER);
        ArgumentCaptor<Courts> savedCourt = ArgumentCaptor.forClass(Courts.class);
        verify(courtRepository).save(savedCourt.capture());
        assertThat(savedCourt.getValue().getCourtId()).isNull();
    }

    @Test
    void ownerCanUpdateOwnTurf() {
        Turf update = new Turf();
        update.setName("Renamed");
        update.setLocation("City");
        update.setOpeningTime(LocalTime.of(6, 0));
        update.setClosingTime(LocalTime.of(7, 0));
        Courts c = new Courts();
        c.setCourtNumber(1L);
        c.setAmountPerHour(600L);
        update.setCourts(List.of(c));

        service.updateTurf(1L, update, OWNER);

        assertThat(turf.getName()).isEqualTo("Renamed");
        assertThat(turf.getOwnerId()).isEqualTo(OWNER);
    }

    @Test
    void ownerCannotUpdateAnotherOwnersTurf() {
        assertThatThrownBy(() -> service.updateTurf(1L, new Turf(), OTHER_OWNER))
                .isInstanceOf(TurfAccessDeniedException.class);
        verify(turfRepository, never()).save(any());
    }

    @Test
    void ownerCannotDeleteAnotherOwnersTurf() {
        assertThatThrownBy(() -> service.deleteOwnedTurf(1L, OTHER_OWNER))
                .isInstanceOf(TurfAccessDeniedException.class);
        verify(turfRepository, never()).delete(any());
    }

    @Test
    void ownerCanDeleteOwnTurf() {
        service.deleteOwnedTurf(1L, OWNER);
        verify(turfRepository).delete(turf);
    }

    @Test
    void legacyTurfWithoutOwnerCannotBeClaimedByAnOwner() {
        turf.setOwnerId(null);
        assertThatThrownBy(() -> service.deleteOwnedTurf(1L, OWNER)).isInstanceOf(TurfAccessDeniedException.class);
        assertThatThrownBy(() -> service.updateTurf(1L, new Turf(), null)).isInstanceOf(TurfAccessDeniedException.class);
    }

    @Test
    void adminDeleteIsNotOwnershipChecked() {
        service.deleteTurf(1L);
        verify(turfRepository).delete(turf);
    }

    @Test
    void missingTurfIsNotFoundNotForbidden() {
        when(turfRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteOwnedTurf(99L, OWNER)).isInstanceOf(TurfNotFoundException.class);
    }

    // ---- slot generation ----

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @SuppressWarnings("unchecked")
    private List<TimeSlots> generate(LocalDate start) {
        service.createTimeSlots(1L, 1L, start, OWNER);
        ArgumentCaptor<List<TimeSlots>> saved = ArgumentCaptor.forClass(List.class);
        verify(timeSlotRepository).saveAll(saved.capture());
        return saved.getValue();
    }

    private TimeSlots slot(long id, LocalDate date, LocalTime start, Status status) {
        TimeSlots s = new TimeSlots();
        s.setTimeSlotId(id);
        s.setSlotDate(date);
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(30));
        s.setPrice(new BigDecimal("250.00"));
        s.setStatus(status);
        if (status == Status.HELD || status == Status.BOOKED) {
            s.setReservationId(100L + id);
        }
        s.setCourt(court);
        return s;
    }

    @Test
    void ownerCannotCreateTimeSlotsOnAnotherOwnersCourt() {
        assertThatThrownBy(() -> service.createTimeSlots(1L, 1L, TOMORROW, OTHER_OWNER))
                .isInstanceOf(TurfAccessDeniedException.class);
        verify(timeSlotRepository, never()).saveAll(any());
    }

    @Test
    void generatesSevenDaysOfFreeSlotsCarryingTheCourtPrice() {
        List<TimeSlots> slots = generate(TOMORROW);

        assertThat(slots).hasSize(14) // 06:00-07:00 in half hours, 7 days
                .allMatch(slot -> slot.getStatus() == Status.FREE && slot.getReservationId() == null)
                .allMatch(slot -> slot.getPrice().compareTo(new BigDecimal("250.00")) == 0);
        assertThat(slots.get(0).getSlotDate()).isEqualTo(TOMORROW);
        assertThat(slots.get(13).getSlotDate()).isEqualTo(TOMORROW.plusDays(6));
    }

    @Test
    void priceIsHalfTheHourlyRateExactly() {
        court.setAmountPerHour(501L);
        assertThat(generate(TOMORROW).get(0).getPrice()).isEqualByComparingTo("250.50");
    }

    @Test
    void slotsThatAlreadyStartedTodayAreSkipped() {
        // Clock is 10:00 and the turf closes at 07:00, so none of today's slots remain.
        List<TimeSlots> slots = generate(TODAY);
        assertThat(slots).hasSize(12);
        assertThat(slots.get(0).getSlotDate()).isEqualTo(TOMORROW);
    }

    @Test
    void overnightTurfPutsPostMidnightSlotsOnTheNextDate() {
        turf.setOpeningTime(LocalTime.of(22, 0));
        turf.setClosingTime(LocalTime.of(0, 30));
        List<TimeSlots> slots = generate(TOMORROW);
        assertThat(slots.subList(0, 3)).extracting(TimeSlots::getSlotDate, TimeSlots::getStartTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TOMORROW, LocalTime.of(22, 0)),
                        org.assertj.core.groups.Tuple.tuple(TOMORROW, LocalTime.of(22, 30)),
                        org.assertj.core.groups.Tuple.tuple(TOMORROW, LocalTime.of(23, 0)));
    }

    @Test
    void startDateMustBeTodayOrLaterAndNotTooFarAhead() {
        assertThatThrownBy(() -> service.createTimeSlots(1L, 1L, TODAY.minusDays(1), OWNER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createTimeSlots(1L, 1L, TODAY.plusDays(61), OWNER))
                .isInstanceOf(IllegalArgumentException.class);
        verify(timeSlotRepository, never()).saveAll(any());
    }

    @Test
    void anyExistingSlotInTheRangeRefusesTheWholeRequest() {
        when(timeSlotRepository.findByCourt_CourtIdAndSlotDateBetween(any(), any(), any()))
                .thenReturn(List.of(slot(1L, TOMORROW.plusDays(3), LocalTime.of(6, 30), Status.BOOKED)));
        assertThatThrownBy(() -> service.createTimeSlots(1L, 1L, TOMORROW, OWNER))
                .isInstanceOf(SlotsAlreadyExistException.class);
        verify(timeSlotRepository, never()).saveAll(any());
    }

    @Test
    void unknownCourtIsNotFound() {
        assertThatThrownBy(() -> service.createTimeSlots(5L, 1L, TOMORROW, OWNER)).isInstanceOf(TurfNotFoundException.class);
    }

    @Test
    void statusIsPersistedAsString() throws Exception {
        Field f = TimeSlots.class.getDeclaredField("status");
        assertThat(f.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        assertThat(new TimeSlots().getStatus()).isEqualTo(Status.FREE);
    }

    // ---- listing ----

    @Test
    void customerOnlySeesFreeOrOccupied() {
        when(timeSlotRepository.findUpcoming(1L, TODAY)).thenReturn(List.of(
                slot(1L, TOMORROW, LocalTime.of(6, 0), Status.FREE),
                slot(2L, TOMORROW, LocalTime.of(6, 30), Status.HELD),
                slot(3L, TOMORROW, LocalTime.of(7, 0), Status.BOOKED),
                slot(4L, TOMORROW, LocalTime.of(7, 30), Status.BLOCKED),
                slot(5L, TODAY, LocalTime.of(9, 0), Status.FREE))); // FREE but already started

        var forCustomer = service.getTimeSlotsForCustomer(1L, null);

        assertThat(forCustomer).extracting(CustomerTimeSlotDTO::status).containsExactly(
                CustomerSlotStatus.FREE, CustomerSlotStatus.OCCUPIED, CustomerSlotStatus.OCCUPIED,
                CustomerSlotStatus.OCCUPIED, CustomerSlotStatus.OCCUPIED);
        assertThat(forCustomer.get(0).price()).isEqualByComparingTo("250.00");
        assertThat(forCustomer.get(0).courtNumber()).isEqualTo(1L);
    }

    @Test
    void ownerSeesRealStatus() {
        when(timeSlotRepository.findOnDate(1L, TOMORROW)).thenReturn(List.of(
                slot(2L, TOMORROW, LocalTime.of(6, 30), Status.HELD),
                slot(4L, TOMORROW, LocalTime.of(7, 30), Status.BLOCKED)));
        assertThat(service.getTimeSlotsForOwner(1L, OWNER, TOMORROW)).extracting(TimeSlotResponseDTO::getStatus)
                .containsExactly(Status.HELD, Status.BLOCKED);
    }

    @Test
    void listingWhenNoneExistIsNotFound() {
        assertThatThrownBy(() -> service.getTimeSlotsForCustomer(1L, null))
                .isInstanceOf(TurfNotFoundException.class).hasMessageContaining("No time slots");
        assertThatThrownBy(() -> service.getTimeSlotsForOwner(1L, OWNER, null))
                .isInstanceOf(TurfNotFoundException.class).hasMessageContaining("No time slots");
    }

    @Test
    void pastDatesHaveNoSlotsAndAreNotQueried() {
        assertThatThrownBy(() -> service.getTimeSlotsForCustomer(1L, TODAY.minusDays(1)))
                .isInstanceOf(TurfNotFoundException.class);
        verify(timeSlotRepository, never()).findOnDate(any(), any());
    }

    @Test
    void listingSlotsOfUnknownTurfIsNotFound() {
        when(turfRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTimeSlotsForCustomer(99L, null)).isInstanceOf(TurfNotFoundException.class);
    }

    @Test
    void ownerCannotListSlotsOfAnotherOwnersTurf() {
        assertThatThrownBy(() -> service.getTimeSlotsForOwner(1L, OTHER_OWNER, null)).isInstanceOf(TurfAccessDeniedException.class);
        verify(timeSlotRepository, never()).findUpcoming(any(), any());
    }

    // ---- owner blocks / frees a slot ----

    private TimeSlots lockedSlot(Status status) {
        TimeSlots s = slot(9L, TOMORROW, LocalTime.of(6, 0), status);
        when(timeSlotRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(s));
        when(timeSlotRepository.save(any(TimeSlots.class))).thenAnswer(i -> i.getArgument(0));
        return s;
    }

    @Test
    void ownerBlocksAFreeSlotAndFreesItAgain() {
        TimeSlots s = lockedSlot(Status.FREE);
        assertThat(service.updateSlotStatus(1L, 9L, Status.BLOCKED, OWNER).getStatus()).isEqualTo(Status.BLOCKED);
        assertThat(service.updateSlotStatus(1L, 9L, Status.FREE, OWNER).getStatus()).isEqualTo(Status.FREE);
        assertThat(s.getReservationId()).isNull();
    }

    @Test
    void settingTheCurrentStatusAgainIsANoOp() {
        lockedSlot(Status.BLOCKED);
        assertThat(service.updateSlotStatus(1L, 9L, Status.BLOCKED, OWNER).getStatus()).isEqualTo(Status.BLOCKED);
        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void heldAndBookedSlotsCannotBeTouchedByTheOwner() {
        for (Status taken : new Status[] { Status.HELD, Status.BOOKED }) {
            TimeSlots s = lockedSlot(taken);
            for (Status target : new Status[] { Status.BLOCKED, Status.FREE }) {
                assertThatThrownBy(() -> service.updateSlotStatus(1L, 9L, target, OWNER))
                        .isInstanceOf(SlotsUnavailableException.class);
            }
            assertThat(s.getStatus()).isEqualTo(taken);
            assertThat(s.getReservationId()).isNotNull();
        }
        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void ownerCannotSetBookingStatusesAndCannotTouchOthersTurfs() {
        lockedSlot(Status.FREE);
        assertThatThrownBy(() -> service.updateSlotStatus(1L, 9L, Status.BOOKED, OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateSlotStatus(1L, 9L, Status.HELD, OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateSlotStatus(1L, 9L, Status.BLOCKED, OTHER_OWNER)).isInstanceOf(TurfAccessDeniedException.class);
        verify(timeSlotRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void slotOfAnotherTurfOrUnknownSlotIsNotFound() {
        Turf other = new Turf();
        other.setTurfId(2L);
        Courts otherCourt = new Courts();
        otherCourt.setTurf(other);
        TimeSlots foreign = slot(8L, TOMORROW, LocalTime.of(6, 0), Status.FREE);
        foreign.setCourt(otherCourt);
        when(timeSlotRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.updateSlotStatus(1L, 8L, Status.BLOCKED, OWNER)).isInstanceOf(TurfNotFoundException.class);
        assertThatThrownBy(() -> service.updateSlotStatus(1L, 404L, Status.BLOCKED, OWNER)).isInstanceOf(TurfNotFoundException.class);
        assertThat(foreign.getStatus()).isEqualTo(Status.FREE);
    }

    @Test
    void slotsThatAlreadyStartedCannotBeChanged() {
        TimeSlots s = slot(9L, TODAY, LocalTime.of(9, 0), Status.FREE);
        when(timeSlotRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.updateSlotStatus(1L, 9L, Status.BLOCKED, OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(s.getStatus()).isEqualTo(Status.FREE);
    }

    // ---- deleting a turf ----

    @Test
    void turfWithHeldOrBookedSlotsCannotBeDeleted() {
        for (Status taken : new Status[] { Status.HELD, Status.BOOKED }) {
            when(timeSlotRepository.lockAllByTurfId(1L)).thenReturn(List.of(
                    slot(1L, TOMORROW, LocalTime.of(6, 0), Status.FREE), slot(2L, TOMORROW, LocalTime.of(6, 30), taken)));
            assertThatThrownBy(() -> service.deleteOwnedTurf(1L, OWNER)).isInstanceOf(SlotsUnavailableException.class);
            assertThatThrownBy(() -> service.deleteTurf(1L)).isInstanceOf(SlotsUnavailableException.class);
        }
        verify(turfRepository, never()).delete(any());
        verify(timeSlotRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void turfWithOnlyFreeOrBlockedSlotsIsDeletedWithItsSlots() {
        List<TimeSlots> slots = List.of(slot(1L, TOMORROW, LocalTime.of(6, 0), Status.FREE),
                slot(2L, TOMORROW, LocalTime.of(6, 30), Status.BLOCKED));
        when(timeSlotRepository.lockAllByTurfId(1L)).thenReturn(slots);
        service.deleteOwnedTurf(1L, OWNER);
        verify(timeSlotRepository).deleteAll(slots);
        verify(turfRepository).delete(turf);
    }

    // ---- search / visibility ----

    @Test
    void hiddenTurfLooksNonexistentToCustomers() {
        turf.setStatus(TurfStatus.HIDDEN);
        assertThatThrownBy(() -> service.getActiveTurfDetails(1L)).isInstanceOf(TurfNotFoundException.class);
        assertThatThrownBy(() -> service.getTimeSlotsForCustomer(1L, null)).isInstanceOf(TurfNotFoundException.class);
    }

    @Test
    void ownerStillSeesSlotsOfOwnHiddenTurf() {
        turf.setStatus(TurfStatus.HIDDEN);
        assertThatThrownBy(() -> service.getTimeSlotsForOwner(1L, OWNER, null))
                .isInstanceOf(TurfNotFoundException.class)
                .hasMessageContaining("No time slots"); // reached the slot lookup, not blocked by visibility
    }

    @Test
    void newTurfStartsWithStatusFromOwnerVisibility() {
        when(visibility.initialStatusFor(OWNER)).thenReturn(TurfStatus.HIDDEN);
        Courts incoming = new Courts();
        incoming.setAmountPerHour(300L);
        service.createTurf(new TurfDTO("New", "Town", null, List.of(incoming), LocalTime.of(6, 0), LocalTime.of(22, 0)), OWNER);
        ArgumentCaptor<Turf> saved = ArgumentCaptor.forClass(Turf.class);
        verify(turfRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TurfStatus.HIDDEN);
    }

    @Test
    void blankLocationListsAllActiveTurfsPaged() {
        when(turfRepository.findByStatus(any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(turf)));
        assertThat(service.searchTurfs("   ", null, 0, 20).content()).hasSize(1);
        verify(turfRepository).findByStatus(any(), any());
        verify(turfRepository, never()).searchByLocation(any(), any(), any());
    }

    @Test
    void locationSearchUsesNormalizedEscapedPattern() {
        when(turfRepository.searchByLocation(any(), any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        service.searchTurfs("  Anna   NAGAR 100%_ ", null, 0, 20);
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(turfRepository).searchByLocation(any(), pattern.capture(), any());
        assertThat(pattern.getValue()).isEqualTo("%anna nagar 100\\%\\_%");
    }

    @Test
    void pagingIsBounded() {
        assertThatThrownBy(() -> service.searchTurfs(null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchTurfs(null, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchTurfs(null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- sports on courts ----

    private static Sports sport(String name) {
        Sports sp = new Sports();
        sp.setSportName(name);
        return sp;
    }

    private static Sports sportWithId(long id, String name) {
        Sports sp = sport(name);
        sp.setSportId(id);
        return sp;
    }

    private Courts incomingCourt(Sports... sports) {
        Courts c = new Courts();
        c.setAmountPerHour(300L);
        c.setAvailableSports(sports.length == 0 ? null : new java.util.ArrayList<>(List.of(sports)));
        return c;
    }

    private com.turfbooking.turf_service.dto.TurfDetailsDTO create(Courts... courts) {
        return service.createTurf(new TurfDTO("New", "Town", null, List.of(courts), LocalTime.of(6, 0), LocalTime.of(22, 0)), OWNER);
    }

    @Test
    void ownersChosenSportsAreSavedOnTheCourtAndReturnedInTheResponse() {
        Sports football = sportWithId(1L, "Football");
        when(sportsRepository.findBySportNameIgnoreCase("Football")).thenReturn(Optional.of(football));

        var result = create(incomingCourt(sport("Football"), sport("Cricket")));

        ArgumentCaptor<Courts> saved = ArgumentCaptor.forClass(Courts.class);
        verify(courtRepository).save(saved.capture());
        assertThat(saved.getValue().getAvailableSports()).extracting(Sports::getSportName).containsExactly("Football", "Cricket");
        assertThat(result.getCourts().get(0).getSports()).extracting("sportName").containsExactly("Cricket", "Football"); // sorted by name
        assertThat(result.getCourts().get(0).getSports()).extracting("sportId").contains(1L);
    }

    @Test
    void eachCourtGetsItsOwnSports() {
        var result = create(incomingCourt(sport("Football")), incomingCourt(sport("Badminton"), sport("Tennis")), incomingCourt());
        assertThat(result.getCourts().get(0).getSports()).extracting("sportName").containsExactly("Football");
        assertThat(result.getCourts().get(1).getSports()).extracting("sportName").containsExactly("Badminton", "Tennis");
        assertThat(result.getCourts().get(2).getSports()).isEmpty(); // choosing no sports is allowed
    }

    @Test
    void sportNamesMatchExistingOnesIgnoringCaseAndSpacingAndAreNotDuplicated() {
        Sports football = sportWithId(1L, "Football");
        when(sportsRepository.findBySportNameIgnoreCase("foot  ball".replaceAll("\\s+", " "))).thenReturn(Optional.empty());
        when(sportsRepository.findBySportNameIgnoreCase("FOOTBALL")).thenReturn(Optional.of(football));

        var result = create(incomingCourt(sport("  FOOTBALL "), sport("football"), sport("Football")));

        assertThat(result.getCourts().get(0).getSports()).hasSize(1);
        verify(sportsRepository, never()).save(any(Sports.class)); // reused the existing sport, created nothing
    }

    @Test
    void aSportIdSentByTheClientIsIgnoredOnlyTheNameCounts() {
        Sports real = sportWithId(1L, "Football");
        when(sportsRepository.findBySportNameIgnoreCase("Football")).thenReturn(Optional.of(real));

        var result = create(incomingCourt(sportWithId(999L, "Football")));

        assertThat(result.getCourts().get(0).getSports()).extracting("sportId").containsExactly(1L);
    }

    @Test
    void invalidSportsAreRejectedBeforeAnythingIsSaved() {
        for (Sports bad : new Sports[] { sport(null), sport("   "), sport("x".repeat(51)) }) {
            assertThatThrownBy(() -> create(incomingCourt(bad))).isInstanceOf(IllegalArgumentException.class);
        }
        Sports[] eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> sport("Sport" + i)).toArray(Sports[]::new);
        assertThatThrownBy(() -> create(incomingCourt(eleven))).isInstanceOf(IllegalArgumentException.class);
        verify(courtRepository, never()).save(any(Courts.class));
    }

    @Test
    void updateReplacesSportsWhenGivenAndKeepsThemWhenOmitted() {
        court.setAvailableSports(new java.util.ArrayList<>(List.of(sportWithId(1L, "Football"))));
        Turf update = new Turf();
        update.setName("Arena"); update.setLocation("City");
        update.setOpeningTime(LocalTime.of(6, 0)); update.setClosingTime(LocalTime.of(7, 0));
        Courts same = new Courts();
        same.setCourtNumber(1L); same.setAmountPerHour(500L);
        update.setCourts(List.of(same));

        service.updateTurf(1L, update, OWNER);                       // sports omitted -> unchanged
        assertThat(court.getAvailableSports()).extracting(Sports::getSportName).containsExactly("Football");

        same.setAvailableSports(new java.util.ArrayList<>(List.of(sport("Cricket"))));
        service.updateTurf(1L, update, OWNER);                       // provided -> replaced
        assertThat(court.getAvailableSports()).extracting(Sports::getSportName).containsExactly("Cricket");

        same.setAvailableSports(new java.util.ArrayList<>());
        service.updateTurf(1L, update, OWNER);                       // empty list -> cleared
        assertThat(court.getAvailableSports()).isEmpty();
    }

    @Test
    void sportsAreListedAlphabetically() {
        when(sportsRepository.findAllByOrderBySportNameAsc()).thenReturn(List.of(sportWithId(2L, "Badminton"), sportWithId(1L, "Football")));
        assertThat(service.listSports()).extracting("sportName").containsExactly("Badminton", "Football");
    }
}
