package com.turfbooking.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.turfbooking.auth_service.config.SecurityConfig;
import com.turfbooking.auth_service.entity.User;
import com.turfbooking.auth_service.enums.Role;
import com.turfbooking.auth_service.service.AuthService;
import com.turfbooking.auth_service.service.UserService;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef")
class UserControllerTest {

    static final String SIGNUP = "{\"email\":\"c@x.com\",\"name\":\"Cathy\",\"password\":\"pw\",\"phoneNumber\":\"+919876543210\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AuthService authService;

    private static User cathy() {
        return new User(42, "c@x.com", "Cathy", "+919876543210", "$2a$10$secretHashSecretHashSecretHash", Role.CUSTOMER);
    }

    @Test
    void signupNeverReturnsThePasswordHash() throws Exception {
        when(userService.saveUser(any())).thenReturn(cathy());
        mvc.perform(post("/users/create").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("c@x.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secretHash"))));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(userService.saveUser(any())).thenThrow(new DataIntegrityViolationException("users_email_key"));
        mvc.perform(post("/users/create").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isConflict());
    }

    @Test
    void signupAndLoginStayOpenToAnonymousCallers() throws Exception {
        when(authService.loginUser(any())).thenReturn("tok");
        mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"c@x.com\",\"password\":\"pw\"}"))
                .andExpect(status().isOk()).andExpect(content().string("tok"));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        when(authService.loginUser(any())).thenThrow(new BadCredentialsException("bad"));
        mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"c@x.com\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookupByEmailNoLongerWorksWithoutLoggingIn() throws Exception {
        mvc.perform(get("/users/c@x.com")).andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void userCanLookUpTheirOwnRecordWithoutTheHash() throws Exception {
        when(userService.findByEmail("c@x.com")).thenReturn(Optional.of(cathy()));
        mvc.perform(get("/users/c@x.com").with(jwt().jwt(j -> j.subject("c@x.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.phoneNumber").value("+919876543210"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void anotherUsersEmailLooksExactlyLikeAnUnknownOne() throws Exception {
        mvc.perform(get("/users/someone-else@x.com").with(jwt().jwt(j -> j.subject("c@x.com")))).andExpect(status().isNotFound());
        verifyNoInteractions(userService);
    }

    @Test
    void otherRoutesRequireAToken() throws Exception {
        mvc.perform(get("/anything")).andExpect(status().isUnauthorized());
        mvc.perform(post("/users/other")).andExpect(status().isUnauthorized());
    }
}
