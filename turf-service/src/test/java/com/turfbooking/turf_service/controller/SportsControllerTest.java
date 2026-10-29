package com.turfbooking.turf_service.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.turfbooking.turf_service.config.SecurityConfig;
import com.turfbooking.turf_service.dto.SportResponseDTO;
import com.turfbooking.turf_service.service.TurfService;

@WebMvcTest(SportsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef")
class SportsControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TurfService turfService;

    @Test
    void sportsListIsPublic() throws Exception {
        when(turfService.listSports()).thenReturn(List.of(new SportResponseDTO(1L, "Football")));
        mvc.perform(get("/sports")).andExpect(status().isOk()).andExpect(jsonPath("$[0].sportName").value("Football"));
        verify(turfService).listSports();
    }

    @Test
    void sportsCannotBeCreatedDirectly() throws Exception {
        mvc.perform(post("/sports").contentType(MediaType.APPLICATION_JSON).content("{\"sportName\":\"Chess\"}"))
                .andExpect(status().isUnauthorized());
    }
}
