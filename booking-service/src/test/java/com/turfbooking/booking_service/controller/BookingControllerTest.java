package com.turfbooking.booking_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.turfbooking.booking_service.client.OwnerClient;
import com.turfbooking.booking_service.config.SecurityConfig;
import com.turfbooking.booking_service.dto.OwnerBookingResponseDTO;
import com.turfbooking.booking_service.exception.BookingConflictException;
import com.turfbooking.booking_service.exception.OwnerAccessDeniedException;
import com.turfbooking.booking_service.exception.OwnerServiceUnavailableException;
import com.turfbooking.booking_service.dto.BookingResponseDTO;
import com.turfbooking.booking_service.dto.PagedResponse;
import com.turfbooking.booking_service.enums.BookingStatus;
import com.turfbooking.booking_service.enums.PaymentStatus;
import com.turfbooking.booking_service.exception.BookingNotFoundException;
import com.turfbooking.booking_service.exception.IdempotencyConflictException;
import com.turfbooking.booking_service.exception.SlotsConflictException;
import com.turfbooking.booking_service.service.BookingService;

@WebMvcTest(BookingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef")
class BookingControllerTest {

    static final String BODY = "{\"slotIds\":[1,2]}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BookingService bookingService;

    @MockitoBean
    OwnerClient ownerClient;

    private static RequestPostProcessor as(String role, boolean fullClaims) {
        return jwt().jwt(j -> {
            j.tokenValue("tok").subject("c@x.com").claim("role", role).claim("uid", 42);
            if (fullClaims) {
                j.claim("name", "Cathy").claim("phone", "+919876543210");
            }
        }).authorities(new SimpleGrantedAuthority(role));
    }

    static final RequestPostProcessor CUSTOMER = as("ROLE_CUSTOMER", true);

    private static BookingResponseDTO dto(BookingStatus status, String failure) {
        return new BookingResponseDTO(5L, status, PaymentStatus.NOT_COLLECTED, 3L, 2L, null, new BigDecimal("500.00"),
                new BigDecimal("500.00"), failure, List.of(), Instant.now());
    }

    private void outcome(BookingStatus status, boolean replay, String failureCode, String failure) {
        when(bookingService.createBooking(any(), eq("k1"), any())).thenReturn(new BookingService.Created(5L, replay));
        when(bookingService.awaitOutcome(5L)).thenReturn(status);
        when(bookingService.getBooking(5L, 42L)).thenReturn(dto(status, failure));
        when(bookingService.failureCode(5L)).thenReturn(failureCode);
    }

    // ---- security ----

    @Test
    void anonymousGets401AndOwnersAdminsGet403() throws Exception {
        mvc.perform(post("/bookings").header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] { "ROLE_OWNER", "ROLE_ADMIN" }) {
            mvc.perform(post("/bookings").with(as(role, true)).header("Idempotency-Key", "k1")
                    .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
            mvc.perform(get("/bookings/5").with(as(role, true))).andExpect(status().isForbidden());
            mvc.perform(get("/bookings").with(as(role, true))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(bookingService);
    }

    @Test
    void tokenWithoutNameOrPhoneMeansLogInAgain() throws Exception {
        mvc.perform(post("/bookings").with(as("ROLE_CUSTOMER", false)).header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(bookingService);
    }

    // ---- POST /bookings outcomes ----

    @Test
    void confirmedBookingIs201WithLocationAndHonestPaymentStatus() throws Exception {
        outcome(BookingStatus.CONFIRMED, false, null, null);
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/bookings/5"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("NOT_COLLECTED"));
    }

    @Test
    void identityComesFromTheTokenNotTheBody() throws Exception {
        outcome(BookingStatus.CONFIRMED, false, null, null);
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotIds\":[1,2],\"customerId\":999,\"totalAmount\":1}")).andExpect(status().isCreated());
        verify(bookingService).createBooking(
                eq(new com.turfbooking.booking_service.security.AuthenticatedUser(42L, "c@x.com", "Cathy", "+919876543210")),
                eq("k1"), eq(List.of(1L, 2L)));
    }

    @Test
    void replayOfAnAlreadyConfirmedBookingIs200() throws Exception {
        outcome(BookingStatus.CONFIRMED, true, null, null);
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void stillProcessingIs202WithLocation() throws Exception {
        outcome(BookingStatus.PENDING_RESERVATION, false, null, null);
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/bookings/5"))
                .andExpect(jsonPath("$.status").value("PENDING_RESERVATION"));
    }

    @Test
    void unavailableSlotsAre409AndInvalidRequestsAre400() throws Exception {
        outcome(BookingStatus.REJECTED, false, "UNAVAILABLE", "One or more of the selected slots are no longer available");
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("One or more of the selected slots are no longer available"))
                .andExpect(jsonPath("$.bookingId").value(5));
        outcome(BookingStatus.REJECTED, false, "INVALID", "All slots in a booking must be on the same court and the same date");
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceErrorsMapToStatusCodes() throws Exception {
        when(bookingService.createBooking(any(), eq("dup"), any())).thenThrow(new IdempotencyConflictException());
        when(bookingService.createBooking(any(), eq("busy"), any())).thenThrow(new SlotsConflictException());
        when(bookingService.createBooking(any(), eq("bad"), any())).thenThrow(new IllegalArgumentException("slotIds must not contain duplicates"));
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "dup").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "busy").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "bad").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingKeyIsPassedOnAndRefusedAsABadRequest() throws Exception {
        when(bookingService.createBooking(any(), eq(null), any())).thenThrow(new IllegalArgumentException("Idempotency-Key header is required"));
        mvc.perform(post("/bookings").with(CUSTOMER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodiesAre400() throws Exception {
        for (String body : new String[] { "{}", "{\"slotIds\":[]}", "{\"slotIds\":[1,2,3,4,5,6,7,8,9,10,11]}",
                "{\"slotIds\":[1,null]}", "not json", "{\"slotIds\":[\"a\"]}" }) {
            mvc.perform(post("/bookings").with(CUSTOMER).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(bookingService);
    }

    // ---- reads ----

    @Test
    void customerReadsOwnBookingAndOthersAre404() throws Exception {
        when(bookingService.getBooking(5L, 42L)).thenReturn(dto(BookingStatus.CONFIRMED, null));
        when(bookingService.getBooking(6L, 42L)).thenThrow(new BookingNotFoundException(6L));
        mvc.perform(get("/bookings/5").with(CUSTOMER)).andExpect(status().isOk()).andExpect(jsonPath("$.bookingId").value(5));
        mvc.perform(get("/bookings/6").with(CUSTOMER)).andExpect(status().isNotFound());
    }

    @Test
    void customerListsOwnBookingsPaged() throws Exception {
        when(bookingService.listBookings(42L, 1, 5)).thenReturn(new PagedResponse<>(List.of(), 1, 5, 0, 0));
        mvc.perform(get("/bookings?page=1&size=5").with(CUSTOMER)).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
    }

    // ---- PUT /bookings/{id}: cancel ----

    @Test
    void onlyCustomersCanCancel() throws Exception {
        mvc.perform(put("/bookings/5")).andExpect(status().isUnauthorized());
        for (String role : new String[] { "ROLE_OWNER", "ROLE_ADMIN" }) {
            mvc.perform(put("/bookings/5").with(as(role, true)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(bookingService);
    }

    @Test
    void cancelWithSlotListIsPartialAndIdentityComesFromTheToken() throws Exception {
        when(bookingService.awaitCancellation(5L)).thenReturn(true);
        when(bookingService.getBooking(5L, 42L)).thenReturn(dto(BookingStatus.PARTIALLY_CANCELLED, null));
        mvc.perform(put("/bookings/5").with(CUSTOMER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancelSlotIds\":[1,2],\"customerId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIALLY_CANCELLED"));
        verify(bookingService).cancel(5L, 42L, List.of(1L, 2L));
    }

    @Test
    void cancelWithoutBodyOrWithEmptyBodyCancelsEverything() throws Exception {
        when(bookingService.awaitCancellation(5L)).thenReturn(true);
        when(bookingService.getBooking(5L, 42L)).thenReturn(dto(BookingStatus.CANCELLED, null));
        mvc.perform(put("/bookings/5").with(CUSTOMER)).andExpect(status().isOk());
        mvc.perform(put("/bookings/5").with(CUSTOMER).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        verify(bookingService, org.mockito.Mockito.times(2)).cancel(5L, 42L, null);
    }

    @Test
    void cancellationNotYetConfirmedByTurfServiceIs202() throws Exception {
        when(bookingService.awaitCancellation(5L)).thenReturn(false);
        when(bookingService.getBooking(5L, 42L)).thenReturn(dto(BookingStatus.CONFIRMED, null));
        mvc.perform(put("/bookings/5").with(CUSTOMER)).andExpect(status().isAccepted());
    }

    @Test
    void cancelErrorsMapToStatusCodes() throws Exception {
        org.mockito.Mockito.doThrow(new BookingConflictException("too late")).when(bookingService).cancel(eq(6L), eq(42L), any());
        org.mockito.Mockito.doThrow(new BookingNotFoundException(7L)).when(bookingService).cancel(eq(7L), eq(42L), any());
        org.mockito.Mockito.doThrow(new IllegalArgumentException("cancelSlotIds must be slots of this booking"))
                .when(bookingService).cancel(eq(8L), eq(42L), any());
        mvc.perform(put("/bookings/6").with(CUSTOMER)).andExpect(status().isConflict());
        mvc.perform(put("/bookings/7").with(CUSTOMER)).andExpect(status().isNotFound());
        mvc.perform(put("/bookings/8").with(CUSTOMER).contentType(MediaType.APPLICATION_JSON).content("{\"cancelSlotIds\":[99]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/bookings/5").with(CUSTOMER).contentType(MediaType.APPLICATION_JSON).content("{\"cancelSlotIds\":[1,null]}"))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /bookings/owner ----

    private static OwnerBookingResponseDTO ownerView() {
        return new OwnerBookingResponseDTO(5L, BookingStatus.CONFIRMED, 3L, 2L, null, "Cathy", "+919876543210",
                new BigDecimal("500.00"), new BigDecimal("500.00"), List.of(), Instant.now());
    }

    @Test
    void onlyOwnersCanUseTheOwnerView() throws Exception {
        mvc.perform(get("/bookings/owner")).andExpect(status().isUnauthorized());
        for (String role : new String[] { "ROLE_CUSTOMER", "ROLE_ADMIN" }) {
            mvc.perform(get("/bookings/owner").with(as(role, true))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(bookingService, ownerClient);
    }

    @Test
    void ownerSeesOnlyTheirOwnerIdsBookingsAndCustomerContact() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenReturn(7L);
        when(bookingService.listOwnerBookings(eq(7L), any(), any(), eq(0), eq(20)))
                .thenReturn(new PagedResponse<>(List.of(ownerView()), 0, 20, 1, 1));
        mvc.perform(get("/bookings/owner?ownerId=999").with(as("ROLE_OWNER", true))) // a client-supplied owner id is ignored
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerName").value("Cathy"))
                .andExpect(jsonPath("$.content[0].customerPhone").value("+919876543210"));
        verify(bookingService).listOwnerBookings(7L, null, null, 0, 20);
    }

    @Test
    void ownerFiltersArePassedOn() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenReturn(7L);
        when(bookingService.listOwnerBookings(any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PagedResponse<>(List.of(), 1, 5, 0, 0));
        mvc.perform(get("/bookings/owner?turfId=3&date=2026-11-02&page=1&size=5").with(as("ROLE_OWNER", true))).andExpect(status().isOk());
        verify(bookingService).listOwnerBookings(7L, 3L, java.time.LocalDate.of(2026, 11, 2), 1, 5);
    }

    @Test
    void ownerWithoutProfileIs403AndOwnerServiceOutageIs503() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenThrow(new OwnerAccessDeniedException());
        mvc.perform(get("/bookings/owner").with(as("ROLE_OWNER", true))).andExpect(status().isForbidden());
        org.mockito.Mockito.doThrow(new OwnerServiceUnavailableException("down", null)).when(ownerClient).resolveOwnerId("tok");
        mvc.perform(get("/bookings/owner").with(as("ROLE_OWNER", true))).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(bookingService);
    }
}
