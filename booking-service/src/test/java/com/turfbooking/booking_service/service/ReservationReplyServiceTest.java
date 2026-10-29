package com.turfbooking.booking_service.service;

import com.turfbooking.booking_service.config.TestMessaging;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.turfbooking.booking_service.entity.Booking;
import com.turfbooking.booking_service.entity.BookingItem;
import com.turfbooking.booking_service.entity.ProcessedMessage;
import com.turfbooking.booking_service.enums.BookingItemStatus;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.event.ConfirmReservationCommand;
import com.turfbooking.booking_service.event.ReleaseSlotsCommand;
import com.turfbooking.booking_service.event.SlotsRejectedEvent;
import com.turfbooking.booking_service.event.SlotsReleasedEvent;
import com.turfbooking.booking_service.event.SlotsReservedEvent;
import com.turfbooking.booking_service.event.SlotsReservedEvent.ReservedSlot;
import com.turfbooking.booking_service.outbox.OutboxService;
import com.turfbooking.booking_service.repository.BookingRepository;
import com.turfbooking.booking_service.repository.ProcessedMessageRepository;

class ReservationReplyServiceTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 28);

    BookingRepository bookings = mock(BookingRepository.class);
    ProcessedMessageRepository processed = mock(ProcessedMessageRepository.class);
    OutboxService outbox = mock(OutboxService.class);
    ReservationReplyService service = new ReservationReplyService(bookings, processed, outbox, TestMessaging.defaults());

    Booking booking;

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setId(500L);
        booking.addItem(BookingItem.requested(1L));
        booking.addItem(BookingItem.requested(2L));
        when(bookings.findByIdForUpdate(500L)).thenReturn(Optional.of(booking));
    }

    static ReservedSlot slot(long id, String hour, String price) {
        LocalTime start = LocalTime.parse(hour);
        return new ReservedSlot(id, DAY, start, start.plusMinutes(30), new BigDecimal(price));
    }

    SlotsReservedEvent reserved(ReservedSlot... slots) {
        return new SlotsReservedEvent(500L, 3L, 7L, 2L, DAY, List.of(slots));
    }

    @Test
    void reservedReplyConfirmsPricesFromTurfServiceAndQueuesConfirm() {
        service.onReserved(reserved(slot(1, "06:00", "250.50"), slot(2, "06:30", "300.00")), "m1");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("550.50");
        assertThat(booking.getCurrentAmount()).isEqualByComparingTo("550.50");
        assertThat(booking.getTurfId()).isEqualTo(3L);
        assertThat(booking.getOwnerId()).isEqualTo(7L);
        assertThat(booking.getCourtNumber()).isEqualTo(2L);
        assertThat(booking.getBookingDate()).isEqualTo(DAY);
        assertThat(booking.getItems()).allMatch(i -> i.getStatus() == BookingItemStatus.ACTIVE && i.getPrice() != null
                && i.getStartTime() != null && i.getActiveSlotId().equals(i.getSlotId()));
        verify(outbox).enqueue("booking.commands", "slots.confirm", "500", new ConfirmReservationCommand(500L));
        verify(processed).save(any(ProcessedMessage.class));
    }

    @Test
    void sameReplyTwiceIsAppliedOnce() {
        when(processed.existsById("m1")).thenReturn(false, true);
        service.onReserved(reserved(slot(1, "06:00", "250.00"), slot(2, "06:30", "250.00")), "m1");
        service.onReserved(reserved(slot(1, "06:00", "999.00"), slot(2, "06:30", "999.00")), "m1");

        assertThat(booking.getTotalAmount()).isEqualByComparingTo("500.00");
        verify(outbox, org.mockito.Mockito.times(1)).enqueue(any(), eq("slots.confirm"), any(), any());
    }

    @Test
    void aRepeatedReplyWithADifferentMessageIdChangesNothingOnceConfirmed() {
        service.onReserved(reserved(slot(1, "06:00", "250.00"), slot(2, "06:30", "250.00")), "m1");
        service.onReserved(reserved(slot(1, "06:00", "999.00"), slot(2, "06:30", "999.00")), "m2");

        assertThat(booking.getTotalAmount()).isEqualByComparingTo("500.00");
        verify(outbox, org.mockito.Mockito.times(1)).enqueue(any(), eq("slots.confirm"), any(), any());
    }

    @Test
    void replyThatDoesNotMatchTheRequestRejectsTheBookingAndUndoesTheReservation() {
        service.onReserved(reserved(slot(1, "06:00", "250.00"), slot(9, "06:30", "250.00")), "m1");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(booking.getFailureCode()).isEqualTo("INVALID");
        assertThat(booking.getItems()).allMatch(i -> i.getStatus() == BookingItemStatus.REJECTED && i.getActiveSlotId() == null);
        verify(outbox).enqueue("booking.commands", "slots.release", "500", new ReleaseSlotsCommand(500L, null));
        verify(outbox, never()).enqueue(any(), eq("slots.confirm"), any(), any());
    }

    @Test
    void replyWithMissingPriceOrMixedDatesIsTreatedAsMismatch() {
        service.onReserved(reserved(slot(1, "06:00", "250.00"),
                new ReservedSlot(2L, DAY.plusDays(1), LocalTime.of(6, 30), LocalTime.of(7, 0), new BigDecimal("1"))), "m1");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
    }

    @Test
    void rejectedReplyFailsThePendingBookingAndFreesItsSlotsForOthers() {
        service.onRejected(new SlotsRejectedEvent(500L, "UNAVAILABLE", "One or more of the selected slots are no longer available"), "m1");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(booking.getFailureCode()).isEqualTo("UNAVAILABLE");
        assertThat(booking.getFailureReason()).contains("no longer available");
        assertThat(booking.getItems()).allMatch(i -> i.getStatus() == BookingItemStatus.REJECTED && i.getActiveSlotId() == null);
        verify(outbox, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void rejectedReplyNeverUndoesAnAlreadyConfirmedBooking() {
        service.onReserved(reserved(slot(1, "06:00", "250.00"), slot(2, "06:30", "250.00")), "m1");
        service.onRejected(new SlotsRejectedEvent(500L, "UNAVAILABLE", "late duplicate"), "m2");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void reservedReplyForARejectedBookingIsUndone() {
        service.onRejected(new SlotsRejectedEvent(500L, "UNAVAILABLE", "x"), "m1");
        service.onReserved(reserved(slot(1, "06:00", "250.00"), slot(2, "06:30", "250.00")), "m2");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        verify(outbox).enqueue("booking.commands", "slots.release", "500", new ReleaseSlotsCommand(500L, null));
    }

    @Test
    void reservedReplyForAnUnknownBookingIsUndone() {
        when(bookings.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        service.onReserved(new SlotsReservedEvent(999L, 3L, 7L, 2L, DAY, List.of(slot(1, "06:00", "1"))), "m1");
        verify(outbox).enqueue("booking.commands", "slots.release", "999", new ReleaseSlotsCommand(999L, null));
    }

    @Test
    void releasedReplyIsRecordedAndChangesNothing() {
        service.onReleased(new SlotsReleasedEvent(500L, List.of(1L, 2L)), "m1");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_RESERVATION);
        ArgumentCaptor<ProcessedMessage> saved = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(processed).save(saved.capture());
        assertThat(saved.getValue().getMessageId()).isEqualTo("m1");
    }

    // ---- cancellation confirmed by turf-service ----

    private void confirmedWithTwoSlots() {
        service.onReserved(reserved(slot(1, "06:00", "250.50"), slot(2, "06:30", "300.00")), "m0");
    }

    private void pending(long... slotIds) {
        for (long id : slotIds) {
            booking.getItems().stream().filter(i -> i.getSlotId() == id).forEach(i -> i.setStatus(BookingItemStatus.RELEASE_PENDING));
        }
    }

    @Test
    void releasedReplyCancelsOnlyTheRequestedSlotsAndKeepsTheAgreedPriceOfTheRest() {
        confirmedWithTwoSlots();
        pending(1);

        service.onReleased(new SlotsReleasedEvent(500L, List.of(1L)), "r1");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PARTIALLY_CANCELLED);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("550.50"); // what was agreed never changes
        assertThat(booking.getCurrentAmount()).isEqualByComparingTo("300.00");
        BookingItem cancelled = booking.getItems().get(0);
        assertThat(cancelled.getStatus()).isEqualTo(BookingItemStatus.CANCELLED);
        assertThat(cancelled.getActiveSlotId()).isNull();       // slot is free for others
        assertThat(cancelled.getPrice()).isEqualByComparingTo("250.50"); // history keeps its price
        assertThat(booking.getItems().get(1).getStatus()).isEqualTo(BookingItemStatus.ACTIVE);
    }

    @Test
    void releasingEverythingCancelsTheBookingAndZeroesWhatIsLeft() {
        confirmedWithTwoSlots();
        pending(1, 2);

        service.onReleased(new SlotsReleasedEvent(500L, List.of(1L, 2L)), "r1");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCurrentAmount()).isEqualByComparingTo("0");
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("550.50");
        assertThat(booking.getItems()).allMatch(i -> i.getStatus() == BookingItemStatus.CANCELLED && i.getCancelledAt() != null);
    }

    @Test
    void secondPartialCancelFinishesTheJob() {
        confirmedWithTwoSlots();
        pending(1);
        service.onReleased(new SlotsReleasedEvent(500L, List.of(1L)), "r1");
        pending(2);
        service.onReleased(new SlotsReleasedEvent(500L, List.of(2L)), "r2");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void repeatedReleasedReplyChangesNothing() {
        confirmedWithTwoSlots();
        pending(1);
        service.onReleased(new SlotsReleasedEvent(500L, List.of(1L)), "r1");
        service.onReleased(new SlotsReleasedEvent(500L, List.of(1L)), "r2"); // different message id, same content
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PARTIALLY_CANCELLED);
        assertThat(booking.getCurrentAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void releasedReplyNeverCancelsSlotsThatNobodyAskedToCancel() {
        confirmedWithTwoSlots();
        pending(1);
        service.onReleased(new SlotsReleasedEvent(500L, List.of(1L, 2L)), "r1"); // slot 2 is ACTIVE, not RELEASE_PENDING
        assertThat(booking.getItems().get(1).getStatus()).isEqualTo(BookingItemStatus.ACTIVE);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PARTIALLY_CANCELLED);
    }

    @Test
    void releasedReplyForARejectedBookingOrUnknownBookingIsHarmless() {
        service.onRejected(new SlotsRejectedEvent(500L, "UNAVAILABLE", "x"), "m1");
        service.onReleased(new SlotsReleasedEvent(500L, List.of(1L, 2L)), "r1");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        when(bookings.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        service.onReleased(new SlotsReleasedEvent(404L, List.of()), "r2");
        service.onReleased(new SlotsReleasedEvent(500L, null), "r3");
    }
}
