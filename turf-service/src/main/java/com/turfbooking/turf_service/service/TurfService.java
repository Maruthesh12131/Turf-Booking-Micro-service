package com.turfbooking.turf_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.turfbooking.turf_service.dto.CustomerTimeSlotDTO;
import com.turfbooking.turf_service.dto.SlotGenerationResponseDTO;
import com.turfbooking.turf_service.enums.CustomerSlotStatus;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turfbooking.turf_service.entity.Turf;
import com.turfbooking.turf_service.enums.Status;
import com.turfbooking.turf_service.enums.TurfStatus;
import com.turfbooking.turf_service.dto.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.turfbooking.turf_service.exception.SlotsAlreadyExistException;
import com.turfbooking.turf_service.exception.SlotsUnavailableException;
import com.turfbooking.turf_service.exception.TurfAccessDeniedException;
import com.turfbooking.turf_service.exception.TurfNotFoundException;
import com.turfbooking.turf_service.repository.SportsRepository;
import com.turfbooking.turf_service.repository.TurfRepository;
import com.turfbooking.turf_service.entity.Sports;
import com.turfbooking.turf_service.dto.SportResponseDTO;
import com.turfbooking.turf_service.dto.TurfDetailsDTO;
import com.turfbooking.turf_service.entity.Courts;
import com.turfbooking.turf_service.entity.TimeSlots;
import com.turfbooking.turf_service.repository.CourtRepository;
import com.turfbooking.turf_service.repository.TimeSlotRepository;
import com.turfbooking.turf_service.dto.CourtResponseDTO;
import com.turfbooking.turf_service.dto.TimeSlotResponseDTO;
import com.turfbooking.turf_service.dto.TurfDTO;

@Service
public class TurfService {

    private final TurfRepository turfRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;

    private final OwnerVisibilityService ownerVisibilityService;
    private final SportsRepository sportsRepository;
    private final Clock clock;

    public TurfService(TurfRepository turfRepository, CourtRepository courtRepository,
            TimeSlotRepository timeSlotRepository, OwnerVisibilityService ownerVisibilityService,
            SportsRepository sportsRepository, Clock clock) {
        this.clock = clock;
        this.sportsRepository = sportsRepository;
        this.turfRepository = turfRepository;
        this.courtRepository = courtRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.ownerVisibilityService = ownerVisibilityService;
    }

    @Transactional
    public TurfDetailsDTO createTurf(TurfDTO turf, Long ownerId) {
        Turf savedTurf = new Turf();
        savedTurf.setOwnerId(ownerId);
        savedTurf.setName(turf.getName());
        savedTurf.setLocation(normalizeText(turf.getLocation()));
        savedTurf.setDescription(turf.getDescription());
        savedTurf.setOpeningTime(roundToHalfHour(turf.getOpeningTime()));
        savedTurf.setClosingTime(roundToHalfHour(turf.getClosingTime()));
        savedTurf.setStatus(ownerVisibilityService.initialStatusFor(ownerId));
        turfRepository.save(savedTurf);
        List<Courts> courtDetails = turf.getCourts();
        List<CourtResponseDTO> courtResponseDTOs = new ArrayList<>();
        long courtNumber = 1;
        for(Courts court : courtDetails) {
            // Courts arrive as entities from the request body: clear any client-supplied id so save() can only insert,
            // never overwrite a court that belongs to someone else.
            court.setCourtId(null);
            court.setCourtNumber(courtNumber);
            courtNumber++;
            court.setTurf(savedTurf);
            court.setAvailableSports(resolveSports(court.getAvailableSports()));
            courtRepository.save(court);
            courtResponseDTOs.add(toCourtResponse(court));
        }
        return new TurfDetailsDTO(
            savedTurf.getTurfId(),
            savedTurf.getName(),
            savedTurf.getLocation(),
            savedTurf.getDescription(),
            savedTurf.getOpeningTime(),
            savedTurf.getClosingTime(),
            courtResponseDTOs,
            savedTurf.getStatus()
        );
    }


    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SPORTS_PER_COURT = 10;
    private static final int MAX_SPORT_NAME_LENGTH = 50;
    private static final int SLOT_MINUTES = 30;
    private static final int GENERATION_DAYS = 7;
    private static final int MAX_START_AHEAD_DAYS = 60;

    /**
     * Customer-facing listing: only ACTIVE turfs. A blank location returns everything; otherwise it is a
     * case-insensitive "contains" match on the trimmed text.
     */
    @Transactional(readOnly = true)
    public PagedResponse<TurfDetailsDTO> searchTurfs(String location, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("turfId"));
        String normalized = location == null ? "" : location.trim();
        Page<Turf> result = normalized.isEmpty()
                ? turfRepository.findByStatus(TurfStatus.ACTIVE, pageable)
                : turfRepository.searchByLocation(TurfStatus.ACTIVE, likePattern(normalized), pageable);
        return PagedResponse.of(result, toDetails(result.getContent()));
    }

    // Stored locations are trimmed with inner whitespace collapsed, so they match the normalized search text.
    private static String normalizeText(String text) {
        return text == null ? null : text.trim().replaceAll("\\s+", " ");
    }

    // Lower-cased, inner whitespace collapsed, and LIKE wildcards in user input escaped so "%" or "_" match literally.
    static String likePattern(String location) {
        String text = location.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Customer-facing detail view: a HIDDEN turf looks exactly like one that does not exist. */
    @Transactional(readOnly = true)
    public TurfDetailsDTO getActiveTurfDetails(Long turfId) {
        Turf turf = getTurfById(turfId);
        if (turf.getStatus() != TurfStatus.ACTIVE) {
            throw new TurfNotFoundException(turfId);
        }
        return toDetails(List.of(turf)).get(0);
    }

    @Transactional(readOnly = true)
    public List<TurfDetailsDTO> getTurfsByOwner(Long ownerId) {
        return toDetails(turfRepository.findByOwnerId(ownerId));
    }

    private List<TurfDetailsDTO> toDetails(List<Turf> turfs) {
        List<TurfDetailsDTO> turfDetailsDTOs = new ArrayList<>();
        for (Turf turf : turfs) {
            List<CourtResponseDTO> courtResponseDTOs = new ArrayList<>();
            for (Courts court : sortedByNumber(turf.getCourts())) {
                courtResponseDTOs.add(toCourtResponse(court));
            }
            turfDetailsDTOs.add(new TurfDetailsDTO(
                turf.getTurfId(),
                turf.getName(),
                turf.getLocation(),
                turf.getDescription(),
                turf.getOpeningTime(),
                turf.getClosingTime(),
                courtResponseDTOs,
                turf.getStatus()
            ));
        }
        return turfDetailsDTOs;
    }

    @Transactional(readOnly = true)
    public List<CourtResponseDTO> getCourtsByTurfId(Long turfId) {
        return sortedByNumber(courtRepository.findByTurf_TurfId(turfId)).stream().map(TurfService::toCourtResponse).toList();
    }

    // The database does not promise any order, so courts are always listed 1, 2, 3...
    private static List<Courts> sortedByNumber(List<Courts> courts) {
        return courts == null ? List.of() : courts.stream().sorted(Comparator.comparing(Courts::getCourtNumber)).toList();
    }

    @Transactional(readOnly = true)
    public List<SportResponseDTO> listSports() {
        return sportsRepository.findAllByOrderBySportNameAsc().stream()
                .map(s -> new SportResponseDTO(s.getSportId(), s.getSportName())).toList();
    }

    private static CourtResponseDTO toCourtResponse(Courts court) {
        List<SportResponseDTO> sports = court.getAvailableSports() == null ? List.of()
                : court.getAvailableSports().stream()
                        .map(s -> new SportResponseDTO(s.getSportId(), s.getSportName()))
                        .sorted(Comparator.comparing(SportResponseDTO::sportName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        return new CourtResponseDTO(court.getCourtNumber(), court.getAmountPerHour(), court.getLength(), court.getWidth(), sports);
    }

    /**
     * Turns the sports in a request into stored ones. Only the name is trusted (a client-supplied sportId is ignored):
     * each name is matched case-insensitively against existing sports and created if new, so "football" and "Football"
     * are one sport. Repeats in the list are dropped.
     */
    private List<Sports> resolveSports(List<Sports> requested) {
        List<Sports> resolved = new ArrayList<>();
        if (requested == null) {
            return resolved;
        }
        if (requested.size() > MAX_SPORTS_PER_COURT) {
            throw new IllegalArgumentException("A court can have at most " + MAX_SPORTS_PER_COURT + " sports");
        }
        Set<String> seen = new HashSet<>();
        for (Sports sport : requested) {
            String name = normalizeText(sport == null ? null : sport.getSportName());
            if (name == null || name.isEmpty() || name.length() > MAX_SPORT_NAME_LENGTH) {
                throw new IllegalArgumentException("Each sport needs a sportName of 1 to " + MAX_SPORT_NAME_LENGTH + " characters");
            }
            if (seen.add(name.toLowerCase(Locale.ROOT))) {
                resolved.add(sportsRepository.findBySportNameIgnoreCase(name).orElseGet(() -> {
                    Sports created = new Sports();
                    created.setSportName(name);
                    return sportsRepository.save(created);
                }));
            }
        }
        return resolved;
    }

    /**
     * Generates 7 days of 30-minute slots for one court, starting at startDate. Each slot copies the court's current
     * price, so later price or schedule changes only affect slots generated afterwards. Slots that already started are
     * skipped; if any slot in the range already exists the whole request is refused and nothing is written.
     */
    @Transactional
    public SlotGenerationResponseDTO createTimeSlots(Long courtNumber, Long turfId, LocalDate startDate, Long ownerId) {
        Turf turf = getOwnedTurf(turfId, ownerId);
        LocalDate today = LocalDate.now(clock);
        if (startDate.isBefore(today) || startDate.isAfter(today.plusDays(MAX_START_AHEAD_DAYS))) {
            throw new IllegalArgumentException("startDate must be between today and " + MAX_START_AHEAD_DAYS + " days ahead");
        }
        Courts court = courtRepository.findByTurf_TurfId(turfId).stream()
                .filter(c -> c.getCourtNumber().equals(courtNumber))
                .findFirst()
                .orElseThrow(() -> new TurfNotFoundException("Court not found with id: " + courtNumber));
        BigDecimal slotPrice = BigDecimal.valueOf(court.getAmountPerHour())
                .multiply(BigDecimal.valueOf(SLOT_MINUTES))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        // Minutes the turf is open; equal opening/closing (e.g. 00:00-00:00) means a full 24 hours,
        // and a closing time earlier than opening wraps past midnight.
        long openMinutes = Duration.between(turf.getOpeningTime(), turf.getClosingTime()).toMinutes();
        if (openMinutes <= 0) {
            openMinutes += 24 * 60;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<TimeSlots> slots = new ArrayList<>();
        for (int day = 0; day < GENERATION_DAYS; day++) {
            LocalDateTime opening = startDate.plusDays(day).atTime(turf.getOpeningTime());
            for (long offset = 0; offset < openMinutes; offset += SLOT_MINUTES) {
                LocalDateTime start = opening.plusMinutes(offset);
                if (!start.isAfter(now)) {
                    continue;
                }
                TimeSlots slot = new TimeSlots();
                slot.setSlotDate(start.toLocalDate());
                slot.setStartTime(start.toLocalTime());
                slot.setEndTime(start.toLocalTime().plusMinutes(SLOT_MINUTES));
                slot.setPrice(slotPrice);
                slot.setStatus(Status.FREE);
                slot.setCourt(court);
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("No upcoming slots fall in the requested period");
        }
        LocalDate first = slots.get(0).getSlotDate();
        LocalDate last = slots.get(slots.size() - 1).getSlotDate();
        Set<String> existing = new HashSet<>();
        for (TimeSlots e : timeSlotRepository.findByCourt_CourtIdAndSlotDateBetween(court.getCourtId(), first, last)) {
            existing.add(e.getSlotDate() + "T" + e.getStartTime());
        }
        for (TimeSlots slot : slots) {
            if (existing.contains(slot.getSlotDate() + "T" + slot.getStartTime())) {
                throw new SlotsAlreadyExistException("Time slots already exist for court number " + courtNumber
                        + " in the requested period");
            }
        }
        timeSlotRepository.saveAll(slots);
        return new SlotGenerationResponseDTO(courtNumber, startDate, startDate.plusDays(GENERATION_DAYS - 1), slots.size());
    }

    /** Customers see FREE or OCCUPIED only, from today on; a FREE slot that has already started counts as OCCUPIED. */
    @Transactional(readOnly = true)
    public List<CustomerTimeSlotDTO> getTimeSlotsForCustomer(Long turfId, LocalDate date) {
        if (getTurfById(turfId).getStatus() != TurfStatus.ACTIVE) {
            throw new TurfNotFoundException(turfId);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<CustomerTimeSlotDTO> slots = findSlots(turfId, date).stream()
                .map(slot -> new CustomerTimeSlotDTO(slot.getTimeSlotId(), slot.getCourt().getCourtNumber(),
                        slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), slot.getPrice(),
                        slot.getStatus() == Status.FREE && startOf(slot).isAfter(now)
                                ? CustomerSlotStatus.FREE : CustomerSlotStatus.OCCUPIED))
                .toList();
        return nonEmpty(slots, turfId);
    }

    /** An owner may view slots, with their real status, only for a turf they own. */
    @Transactional(readOnly = true)
    public List<TimeSlotResponseDTO> getTimeSlotsForOwner(Long turfId, Long ownerId, LocalDate date) {
        getOwnedTurf(turfId, ownerId);
        return nonEmpty(findSlots(turfId, date).stream().map(TurfService::toOwnerView).toList(), turfId);
    }

    private List<TimeSlots> findSlots(Long turfId, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date == null) {
            return timeSlotRepository.findUpcoming(turfId, today);
        }
        return date.isBefore(today) ? List.of() : timeSlotRepository.findOnDate(turfId, date);
    }

    private static <T> List<T> nonEmpty(List<T> slots, Long turfId) {
        if (slots.isEmpty()) {
            throw new TurfNotFoundException("No time slots have been created for turf with id: " + turfId);
        }
        return slots;
    }

    private static LocalDateTime startOf(TimeSlots slot) {
        return LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
    }

    private static TimeSlotResponseDTO toOwnerView(TimeSlots slot) {
        return new TimeSlotResponseDTO(slot.getTimeSlotId(), slot.getCourt().getCourtNumber(), slot.getSlotDate(),
                slot.getStartTime(), slot.getEndTime(), slot.getPrice(), slot.getStatus());
    }

    /**
     * Owner blocks a free slot or frees a blocked one. The slot row is locked first, so this cannot interleave with a
     * reservation: a slot that is HELD or BOOKED by then is refused with 409, never overwritten.
     */
    @Transactional
    public TimeSlotResponseDTO updateSlotStatus(Long turfId, Long slotId, Status target, Long ownerId) {
        getOwnedTurf(turfId, ownerId);
        if (target != Status.FREE && target != Status.BLOCKED) {
            throw new IllegalArgumentException("status must be FREE or BLOCKED");
        }
        TimeSlots slot = timeSlotRepository.findByIdForUpdate(slotId)
                .filter(s -> s.getCourt().getTurf().getTurfId().equals(turfId))
                .orElseThrow(() -> new TurfNotFoundException("Time slot not found with id: " + slotId));
        if (!startOf(slot).isAfter(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("Slots that have already started cannot be changed");
        }
        if (slot.getStatus() == target) {
            return toOwnerView(slot);
        }
        if (slot.getStatus() == Status.HELD || slot.getStatus() == Status.BOOKED) {
            throw new SlotsUnavailableException("Slot is " + slot.getStatus() + " and cannot be changed");
        }
        slot.setStatus(target);
        return toOwnerView(timeSlotRepository.save(slot));
    }

    public Turf getTurfById(Long turfId) {
        return turfRepository.findById(turfId)
                .orElseThrow(() -> new TurfNotFoundException(turfId));
    }

    // Existence is reported first (404); a turf that exists but is not this owner's is a generic 403.
    // Turfs with no owner (created before ownership existed) are not claimable by anyone.
    private Turf getOwnedTurf(Long turfId, Long ownerId) {
        Turf turf = getTurfById(turfId);
        if (ownerId == null || !ownerId.equals(turf.getOwnerId())) {
            throw new TurfAccessDeniedException();
        }
        return turf;
    }

    @Transactional 
    public TurfDetailsDTO updateTurf(Long turfId, Turf updatedTurf, Long ownerId) {
        Turf existingTurf = getOwnedTurf(turfId, ownerId);
        existingTurf.setName(updatedTurf.getName());
        existingTurf.setLocation(normalizeText(updatedTurf.getLocation()));
        existingTurf.setDescription(updatedTurf.getDescription());
        existingTurf.setOpeningTime(roundToHalfHour(updatedTurf.getOpeningTime()));
        existingTurf.setClosingTime(roundToHalfHour(updatedTurf.getClosingTime()));
        for (Courts updatedCourt : updatedTurf.getCourts()) {
            Courts existingCourt = existingTurf.getCourts().stream()
                    .filter(court -> court.getCourtNumber().equals(updatedCourt.getCourtNumber()))
                    .findFirst()
                    .orElseThrow(() -> new TurfNotFoundException("Court not found with number: " + updatedCourt.getCourtNumber()));
            existingCourt.setAmountPerHour(updatedCourt.getAmountPerHour());
            existingCourt.setLength(updatedCourt.getLength());
            existingCourt.setWidth(updatedCourt.getWidth());
            // Left unchanged if the request does not mention sports; replaced by exactly this list if it does.
            if (updatedCourt.getAvailableSports() != null) {
                existingCourt.setAvailableSports(resolveSports(updatedCourt.getAvailableSports()));
            }
        }
        TurfDetailsDTO updatedTurfDetailsDTO = new TurfDetailsDTO(
                existingTurf.getTurfId(),
                existingTurf.getName(),
                existingTurf.getLocation(),
                existingTurf.getDescription(),
                existingTurf.getOpeningTime(),
                existingTurf.getClosingTime(),
                getCourtsByTurfId(turfId),
                existingTurf.getStatus()
        );
        turfRepository.save(existingTurf);
        return updatedTurfDetailsDTO;
    }

    @Transactional 
    public void deleteOwnedTurf(Long turfId, Long ownerId) {
        getOwnedTurf(turfId, ownerId);
        deleteTurf(turfId);
    }

    /**
     * Unchecked delete; callers must have authorized it (ADMIN, or via deleteOwnedTurf). Refused while any slot is
     * HELD or BOOKED, since a booking must never be left pointing at a slot that no longer exists. The slots are
     * locked first, so a reservation running at the same time either finishes before this check or waits for it.
     */
    @Transactional 
    public void deleteTurf(Long turfId) {
        Turf existingTurf = getTurfById(turfId);
        List<TimeSlots> slots = timeSlotRepository.lockAllByTurfId(turfId);
        if (slots.stream().anyMatch(s -> s.getStatus() == Status.HELD || s.getStatus() == Status.BOOKED)) {
            throw new SlotsUnavailableException("Turf has active reservations or bookings and cannot be deleted");
        }
        timeSlotRepository.deleteAll(slots);
        courtRepository.deleteAll(courtRepository.findByTurf_TurfId(turfId));
        turfRepository.delete(existingTurf);
    }

    // Rounds to the nearest :00 or :30, with :15 and :45 rounding up (23:45 becomes 00:00).
    private static LocalTime roundToHalfHour(LocalTime time) {
        if (time == null) {
            return null;
        }
        int minutes = time.getHour() * 60 + time.getMinute();
        int rounded = ((minutes + 15) / 30) * 30;
        return LocalTime.ofSecondOfDay((rounded % (24 * 60)) * 60L);
    }
}
