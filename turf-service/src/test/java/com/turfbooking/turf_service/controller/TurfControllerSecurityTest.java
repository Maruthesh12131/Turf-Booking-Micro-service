package com.turfbooking.turf_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.turfbooking.turf_service.client.OwnerClient;
import com.turfbooking.turf_service.config.SecurityConfig;
import com.turfbooking.turf_service.dto.PagedResponse;
import com.turfbooking.turf_service.exception.OwnerNotVerifiedException;
import com.turfbooking.turf_service.exception.OwnerServiceUnavailableException;
import com.turfbooking.turf_service.exception.TurfNotFoundException;
import com.turfbooking.turf_service.exception.TurfAccessDeniedException;
import com.turfbooking.turf_service.service.TurfService;

@WebMvcTest(TurfController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef")
class TurfControllerSecurityTest {

    private static final String SLOTS_JSON = "{\"turfId\":1,\"startDate\":\"2026-11-01\"}";

    private static final String TURF_JSON = """
            {"name":"Arena","location":"City","openingTime":"06:00","closingTime":"22:00",
             "courts":[{"amountPerHour":500}]}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TurfService turfService;

    @MockitoBean
    OwnerClient ownerClient;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.claim("role", role).claim("uid", 1L).tokenValue("tok"))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static final RequestPostProcessor OWNER = as("ROLE_OWNER");
    private static final RequestPostProcessor ADMIN = as("ROLE_ADMIN");
    private static final RequestPostProcessor CUSTOMER = as("ROLE_CUSTOMER");

    // ---- unauthenticated ----

    @Test
    void anonymousCanBrowseTurfs() throws Exception {
        when(turfService.searchTurfs(any(), eq(0), eq(20))).thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));
        when(turfService.getActiveTurfDetails(1L)).thenReturn(null);
        mvc.perform(get("/turfs")).andExpect(status().isOk());
        mvc.perform(get("/turfs/1")).andExpect(status().isOk());
    }

    @Test
    void anonymousCannotUseProtectedOperations() throws Exception {
        mvc.perform(post("/turfs").contentType(MediaType.APPLICATION_JSON).content(TURF_JSON)).andExpect(status().isUnauthorized());
        mvc.perform(put("/turfs/1").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/turfs/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/turfs/courts/1/timeslots").contentType(MediaType.APPLICATION_JSON).content(SLOTS_JSON)).andExpect(status().isUnauthorized());
        mvc.perform(get("/turfs/mine")).andExpect(status().isUnauthorized());
        verifyNoInteractions(turfService, ownerClient);
    }

    @Test
    void garbageTokenIsUnauthorized() throws Exception {
        mvc.perform(delete("/turfs/1").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    // ---- customer ----

    @Test
    void customerCannotCreateUpdateDeleteOrAddSlots() throws Exception {
        mvc.perform(post("/turfs").with(CUSTOMER).contentType(MediaType.APPLICATION_JSON).content(TURF_JSON)).andExpect(status().isForbidden());
        mvc.perform(put("/turfs/1").with(CUSTOMER).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/turfs/1").with(CUSTOMER)).andExpect(status().isForbidden());
        mvc.perform(post("/turfs/courts/1/timeslots").with(CUSTOMER).contentType(MediaType.APPLICATION_JSON).content(SLOTS_JSON)).andExpect(status().isForbidden());
        mvc.perform(get("/turfs/mine").with(CUSTOMER)).andExpect(status().isForbidden());
        verifyNoInteractions(turfService, ownerClient);
    }

    // ---- owner ----

    @Test
    void ownerCreatesTurfForResolvedOwnerIdNotBodyOwnerId() throws Exception {
        when(ownerClient.resolveVerifiedOwnerId("tok")).thenReturn(7L);
        String withSpoofedOwner = TURF_JSON.replace("{\"name\"", "{\"ownerId\":999,\"name\"");
        mvc.perform(post("/turfs").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(withSpoofedOwner))
                .andExpect(status().isCreated());
        verify(turfService).createTurf(any(), eq(7L));
    }

    @Test
    void unverifiedOwnerCannotCreateTurf() throws Exception {
        when(ownerClient.resolveVerifiedOwnerId("tok")).thenThrow(new OwnerNotVerifiedException());
        mvc.perform(post("/turfs").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(TURF_JSON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(turfService);
    }

    @Test
    void turfCreationFailsClosedWhenOwnerServiceIsDown() throws Exception {
        when(ownerClient.resolveVerifiedOwnerId("tok")).thenThrow(new OwnerServiceUnavailableException("down", null));
        mvc.perform(post("/turfs").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(TURF_JSON))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(turfService);
    }

    @Test
    void searchPassesLocationAndPaging() throws Exception {
        when(turfService.searchTurfs("chen", 2, 5)).thenReturn(new PagedResponse<>(List.of(), 2, 5, 0, 0));
        mvc.perform(get("/turfs?location=chen&page=2&size=5")).andExpect(status().isOk());
        verify(turfService).searchTurfs("chen", 2, 5);
    }

    @Test
    void invalidPagingIsBadRequestAndHiddenTurfIsNotFound() throws Exception {
        when(turfService.searchTurfs(any(), eq(0), eq(1000))).thenThrow(new IllegalArgumentException("bad paging"));
        mvc.perform(get("/turfs?size=1000")).andExpect(status().isBadRequest());
        when(turfService.getActiveTurfDetails(9L)).thenThrow(new TurfNotFoundException(9L));
        mvc.perform(get("/turfs/9")).andExpect(status().isNotFound());
    }

    @Test
    void ownerUpdatesDeletesAndAddsSlotsWithResolvedOwnerId() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenReturn(7L);
        mvc.perform(put("/turfs/3").with(OWNER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"location\":\"B\",\"openingTime\":\"06:00:00\",\"closingTime\":\"22:00:00\",\"courts\":[{\"courtNumber\":1,\"amountPerHour\":500}]}"))
                .andExpect(status().isOk());
        verify(turfService).updateTurf(eq(3L), any(), eq(7L));

        mvc.perform(delete("/turfs/3").with(OWNER)).andExpect(status().isNoContent());
        verify(turfService).deleteOwnedTurf(3L, 7L);

        mvc.perform(post("/turfs/courts/2/timeslots").with(OWNER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"turfId\":3,\"startDate\":\"2026-11-01\"}"))
                .andExpect(status().isCreated());
        verify(turfService).createTimeSlots(2L, 3L, java.time.LocalDate.of(2026, 11, 1), 7L);
    }

    @Test
    void ownerTouchingSomeoneElsesTurfGetsForbidden() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenReturn(7L);
        org.mockito.Mockito.doThrow(new TurfAccessDeniedException()).when(turfService).deleteOwnedTurf(3L, 7L);
        mvc.perform(delete("/turfs/3").with(OWNER)).andExpect(status().isForbidden());
    }

    @Test
    void ownerWithoutProfileIsForbidden() throws Exception {
        when(ownerClient.resolveVerifiedOwnerId("tok")).thenThrow(new TurfAccessDeniedException());
        mvc.perform(post("/turfs").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(TURF_JSON)).andExpect(status().isForbidden());
        verifyNoInteractions(turfService);
    }

    @Test
    void ownerServiceOutageIsServiceUnavailable() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenThrow(new OwnerServiceUnavailableException("down", null));
        mvc.perform(delete("/turfs/3").with(OWNER)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void ownerListsOnlyOwnTurfs() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenReturn(7L);
        when(turfService.getTurfsByOwner(7L)).thenReturn(List.of());
        mvc.perform(get("/turfs/mine").with(OWNER)).andExpect(status().isOk());
        verify(turfService).getTurfsByOwner(7L);
    }

    // ---- admin ----

    @Test
    void adminCanDeleteWithoutOwnerLookup() throws Exception {
        mvc.perform(delete("/turfs/3").with(ADMIN)).andExpect(status().isNoContent());
        verify(turfService).deleteTurf(3L);
        verifyNoInteractions(ownerClient);
    }

    @Test
    void adminCannotCreateUpdateOrAddSlots() throws Exception {
        mvc.perform(post("/turfs").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(TURF_JSON)).andExpect(status().isForbidden());
        mvc.perform(put("/turfs/1").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/turfs/courts/1/timeslots").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(SLOTS_JSON)).andExpect(status().isForbidden());
        mvc.perform(get("/turfs/mine").with(ADMIN)).andExpect(status().isForbidden());
        verifyNoInteractions(turfService, ownerClient);
    }

    // ---- GET /turfs/{id}/timeslots ----

    @Test
    void anonymousAndAdminCannotListTimeSlots() throws Exception {
        mvc.perform(get("/turfs/3/timeslots")).andExpect(status().isUnauthorized());
        mvc.perform(get("/turfs/3/timeslots").with(ADMIN)).andExpect(status().isForbidden());
        verifyNoInteractions(turfService, ownerClient);
    }

    @Test
    void customerListsSlotsWithoutOwnerLookup() throws Exception {
        when(turfService.getTimeSlotsForCustomer(eq(3L), any())).thenReturn(List.of(
                new com.turfbooking.turf_service.dto.CustomerTimeSlotDTO(1L, 1L, java.time.LocalDate.of(2026, 11, 1),
                        java.time.LocalTime.of(6, 0), java.time.LocalTime.of(6, 30), new java.math.BigDecimal("250.00"),
                        com.turfbooking.turf_service.enums.CustomerSlotStatus.FREE)));
        mvc.perform(get("/turfs/3/timeslots").with(CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].status").value("FREE"));
        verifyNoInteractions(ownerClient);
    }

    @Test
    void ownerListsSlotsOnlyForOwnTurf() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenReturn(7L);
        when(turfService.getTimeSlotsForOwner(eq(3L), eq(7L), any())).thenReturn(List.of());
        mvc.perform(get("/turfs/3/timeslots").with(OWNER)).andExpect(status().isOk());
        verify(turfService).getTimeSlotsForOwner(eq(3L), eq(7L), org.mockito.ArgumentMatchers.isNull());

        org.mockito.Mockito.doThrow(new TurfAccessDeniedException()).when(turfService).getTimeSlotsForOwner(eq(4L), eq(7L), any());
        mvc.perform(get("/turfs/4/timeslots").with(OWNER)).andExpect(status().isForbidden());
    }

    @Test
    void noTimeSlotsIsNotFoundWithMessage() throws Exception {
        when(turfService.getTimeSlotsForCustomer(eq(3L), any())).thenThrow(new TurfNotFoundException("No time slots have been created for turf with id: 3"));
        mvc.perform(get("/turfs/3/timeslots").with(CUSTOMER))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("No time slots have been created for turf with id: 3"));
    }

    @Test
    void dateFilterIsPassedToTheService() throws Exception {
        when(turfService.getTimeSlotsForCustomer(eq(3L), any())).thenReturn(List.of());
        mvc.perform(get("/turfs/3/timeslots?date=2026-11-02").with(CUSTOMER)).andExpect(status().isOk());
        verify(turfService).getTimeSlotsForCustomer(3L, java.time.LocalDate.of(2026, 11, 2));
        mvc.perform(get("/turfs/3/timeslots?date=notadate").with(CUSTOMER)).andExpect(status().isBadRequest());
    }

    @Test
    void slotGenerationRequiresTurfIdAndStartDate() throws Exception {
        mvc.perform(post("/turfs/courts/1/timeslots").with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/turfs/courts/1/timeslots").with(OWNER).contentType(MediaType.APPLICATION_JSON).content("3"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(turfService);
    }

    // ---- PUT /turfs/{id}/timeslots/{slotId}/status ----

    private static final String BLOCK = "{\"status\":\"BLOCKED\"}";

    @Test
    void onlyAnOwnerCanChangeSlotStatus() throws Exception {
        mvc.perform(put("/turfs/3/timeslots/9/status").contentType(MediaType.APPLICATION_JSON).content(BLOCK))
                .andExpect(status().isUnauthorized());
        for (RequestPostProcessor who : new RequestPostProcessor[] { CUSTOMER, ADMIN }) {
            mvc.perform(put("/turfs/3/timeslots/9/status").with(who).contentType(MediaType.APPLICATION_JSON).content(BLOCK))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turfService, ownerClient);
    }

    @Test
    void ownerChangesSlotStatusWithResolvedOwnerId() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenReturn(7L);
        mvc.perform(put("/turfs/3/timeslots/9/status").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(BLOCK))
                .andExpect(status().isOk());
        verify(turfService).updateSlotStatus(3L, 9L, com.turfbooking.turf_service.enums.Status.BLOCKED, 7L);
    }

    @Test
    void slotStatusErrorsMapToStatusCodes() throws Exception {
        when(ownerClient.resolveOwnerId("tok")).thenReturn(7L);
        org.mockito.Mockito.doThrow(new com.turfbooking.turf_service.exception.SlotsUnavailableException("Slot is BOOKED"))
                .when(turfService).updateSlotStatus(eq(3L), eq(9L), any(), eq(7L));
        mvc.perform(put("/turfs/3/timeslots/9/status").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(BLOCK))
                .andExpect(status().isConflict());
        org.mockito.Mockito.doThrow(new TurfAccessDeniedException())
                .when(turfService).updateSlotStatus(eq(4L), eq(9L), any(), eq(7L));
        mvc.perform(put("/turfs/4/timeslots/9/status").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(BLOCK))
                .andExpect(status().isForbidden());
        mvc.perform(put("/turfs/3/timeslots/9/status").with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/turfs/3/timeslots/9/status").with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OCCUPIED\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- sports in the JSON ----

    @Test
    void sportsInTheRequestJsonAreBoundToEachCourt() throws Exception {
        when(ownerClient.resolveVerifiedOwnerId("tok")).thenReturn(7L);
        String json = """
                {"name":"Arena","location":"City","openingTime":"06:00","closingTime":"22:00",
                 "courts":[{"amountPerHour":500,"sports":[{"sportName":"Football"},{"sportName":"Cricket"}]},
                           {"amountPerHour":400,"availableSports":[{"sportName":"Tennis"}]},
                           {"amountPerHour":300}]}""";
        mvc.perform(post("/turfs").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<com.turfbooking.turf_service.dto.TurfDTO> dto =
                org.mockito.ArgumentCaptor.forClass(com.turfbooking.turf_service.dto.TurfDTO.class);
        verify(turfService).createTurf(dto.capture(), eq(7L));
        var courts = dto.getValue().getCourts();
        org.assertj.core.api.Assertions.assertThat(courts.get(0).getAvailableSports())
                .extracting(com.turfbooking.turf_service.entity.Sports::getSportName).containsExactly("Football", "Cricket");
        org.assertj.core.api.Assertions.assertThat(courts.get(1).getAvailableSports())
                .extracting(com.turfbooking.turf_service.entity.Sports::getSportName).containsExactly("Tennis"); // old key still accepted
        org.assertj.core.api.Assertions.assertThat(courts.get(2).getAvailableSports()).isNull();
    }

    @Test
    void invalidSportsAreA400() throws Exception {
        when(ownerClient.resolveVerifiedOwnerId("tok")).thenReturn(7L);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("A court can have at most 10 sports"))
                .when(turfService).createTurf(any(), eq(7L));
        mvc.perform(post("/turfs").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(TURF_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void courtResponseCarriesTheSportsWithoutLoopingBackToCourts() throws Exception {
        var court = new com.turfbooking.turf_service.dto.CourtResponseDTO(1L, 500L, null, null,
                List.of(new com.turfbooking.turf_service.dto.SportResponseDTO(1L, "Football")));
        when(turfService.getActiveTurfDetails(3L)).thenReturn(new com.turfbooking.turf_service.dto.TurfDetailsDTO(
                3L, "Arena", "City", null, java.time.LocalTime.of(6, 0), java.time.LocalTime.of(22, 0), List.of(court),
                com.turfbooking.turf_service.enums.TurfStatus.ACTIVE));
        mvc.perform(get("/turfs/3"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.courts[0].sports[0].sportName").value("Football"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.courts[0].sports[0].courts").doesNotExist());
    }
}
