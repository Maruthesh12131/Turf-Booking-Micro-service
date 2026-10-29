package com.turfbooking.owner_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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

import com.turfbooking.owner_service.config.SecurityConfig;
import com.turfbooking.owner_service.dto.OwnerResponseDTO;
import com.turfbooking.owner_service.enums.VerificationStatus;
import com.turfbooking.owner_service.exception.InvalidVerificationRequestException;
import com.turfbooking.owner_service.exception.InvalidVerificationTransitionException;
import com.turfbooking.owner_service.exception.OwnerAlreadyExistsException;
import com.turfbooking.owner_service.exception.OwnerNotFoundException;
import com.turfbooking.owner_service.service.OwnerService;

@WebMvcTest(OwnerController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef")
class OwnerControllerSecurityTest {

    private static final String BODY = "{\"businessName\":\"Arena Co\",\"phoneNumber\":\"+919876543210\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OwnerService ownerService;

    private static RequestPostProcessor as(String role, Object uid) {
        return jwt().jwt(j -> {
            j.claim("role", role);
            if (uid != null) {
                j.claim("uid", uid);
            }
        }).authorities(new SimpleGrantedAuthority(role));
    }

    private static OwnerResponseDTO profile() {
        return new OwnerResponseDTO(5L, "Arena Co", "+919876543210", VerificationStatus.PENDING, Instant.now());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/owners/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/owners").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(ownerService);
    }

    @Test
    void customerAndAdminAreForbidden() throws Exception {
        for (String role : new String[] { "ROLE_CUSTOMER", "ROLE_ADMIN" }) {
            mvc.perform(get("/owners/me").with(as(role, 1L))).andExpect(status().isForbidden());
            mvc.perform(post("/owners").with(as(role, 1L)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(ownerService);
    }

    @Test
    void ownerProfileIsScopedToTokenUserIdNotBody() throws Exception {
        when(ownerService.createOwner(eq(42L), any())).thenReturn(profile());
        String spoofed = "{\"userId\":1,\"ownerId\":1,\"verificationStatus\":\"VERIFIED\"," + BODY.substring(1);
        mvc.perform(post("/owners").with(as("ROLE_OWNER", 42L)).contentType(MediaType.APPLICATION_JSON).content(spoofed))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.userId").doesNotExist());
        verify(ownerService).createOwner(eq(42L), any());

        when(ownerService.getByUserId(42L)).thenReturn(profile());
        mvc.perform(get("/owners/me").with(as("ROLE_OWNER", 42L))).andExpect(status().isOk());
        verify(ownerService).getByUserId(42L);

        when(ownerService.updateOwner(eq(42L), any())).thenReturn(profile());
        mvc.perform(put("/owners/me").with(as("ROLE_OWNER", 42L)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
    }

    @Test
    void tokenWithoutUidIsRejected() throws Exception {
        mvc.perform(get("/owners/me").with(as("ROLE_OWNER", null))).andExpect(status().isForbidden());
        verifyNoInteractions(ownerService);
    }

    @Test
    void duplicateProfileIsConflictAndMissingProfileIsNotFound() throws Exception {
        when(ownerService.createOwner(eq(42L), any())).thenThrow(new OwnerAlreadyExistsException("exists"));
        mvc.perform(post("/owners").with(as("ROLE_OWNER", 42L)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());

        when(ownerService.getByUserId(42L)).thenThrow(new OwnerNotFoundException("none"));
        mvc.perform(get("/owners/me").with(as("ROLE_OWNER", 42L))).andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyIsBadRequest() throws Exception {
        mvc.perform(post("/owners").with(as("ROLE_OWNER", 42L)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\":\"\",\"phoneNumber\":\"abc\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(ownerService);
    }

    // ---- admin verification ----

    private static final String VERIFY = "{\"status\":\"VERIFIED\"}";

    @Test
    void onlyAdminCanDecideVerification() throws Exception {
        mvc.perform(put("/owners/5/verification").contentType(MediaType.APPLICATION_JSON).content(VERIFY))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] { "ROLE_OWNER", "ROLE_CUSTOMER" }) {
            mvc.perform(put("/owners/5/verification").with(as(role, 1)).contentType(MediaType.APPLICATION_JSON).content(VERIFY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(ownerService);
    }

    @Test
    void adminDecisionUsesTokenIdentityAndReturnsOk() throws Exception {
        when(ownerService.updateVerification(eq(5L), any(), eq(9L))).thenReturn(profile());
        mvc.perform(put("/owners/5/verification").with(as("ROLE_ADMIN", 9)).contentType(MediaType.APPLICATION_JSON).content(VERIFY))
                .andExpect(status().isOk());
    }

    @Test
    void missingOrUnknownStatusIsBadRequest() throws Exception {
        for (String body : new String[] { "{}", "{\"status\":\"APPROVED\"}" }) {
            mvc.perform(put("/owners/5/verification").with(as("ROLE_ADMIN", 9)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(ownerService);
    }

    @Test
    void serviceErrorsMapToStatusCodes() throws Exception {
        when(ownerService.updateVerification(eq(404L), any(), any())).thenThrow(new OwnerNotFoundException("Owner not found"));
        when(ownerService.updateVerification(eq(6L), any(), any()))
                .thenThrow(new InvalidVerificationTransitionException("already VERIFIED"));
        when(ownerService.updateVerification(eq(7L), any(), any()))
                .thenThrow(new InvalidVerificationRequestException("reason required"));
        mvc.perform(put("/owners/404/verification").with(as("ROLE_ADMIN", 9)).contentType(MediaType.APPLICATION_JSON).content(VERIFY))
                .andExpect(status().isNotFound());
        mvc.perform(put("/owners/6/verification").with(as("ROLE_ADMIN", 9)).contentType(MediaType.APPLICATION_JSON).content(VERIFY))
                .andExpect(status().isConflict());
        mvc.perform(put("/owners/7/verification").with(as("ROLE_ADMIN", 9)).contentType(MediaType.APPLICATION_JSON).content(VERIFY))
                .andExpect(status().isBadRequest());
    }
}
