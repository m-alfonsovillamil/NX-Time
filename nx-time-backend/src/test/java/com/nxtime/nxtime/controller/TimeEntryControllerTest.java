package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryResponse;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.service.TimeEntryService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de TimeEntryController: solo la capa web (rutas,
 * authorities de {@code @PreAuthorize}, validación de {@code @Valid} y el
 * mapeo a ProblemDetail vía GlobalExceptionHandler). La lógica de negocio
 * real (la máquina de estados) la cubre {@link
 * com.nxtime.nxtime.service.impl.TimeEntryServiceImplTest}.
 */
@NxTimeWebMvcTest(TimeEntryController.class)
@Import(WebMvcTestSecurityConfig.class)
class TimeEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private TimeEntryMapper timeEntryMapper;

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:escribir")
    @DisplayName("POST /fichaje con la authority correcta y cuerpo válido devuelve 200")
    void registerTimeEntry_conAuthorityYCuerpoValido_devuelve200() throws Exception {
        TimeEntry entry = TimeEntry.builder().id(1L).build();
        when(timeEntryService.registerTimeEntry(eq("empleado@nxtime.test"), any())).thenReturn(entry);
        when(timeEntryMapper.toResponse(entry))
                .thenReturn(new TimeEntryResponse(1L, Instant.now(), null, false, 0));

        mockMvc.perform(post("/api/v1/fichaje")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INICIO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("POST /fichaje sin la authority 'fichaje:escribir' devuelve 403")
    void registerTimeEntry_sinAuthorityDeEscritura_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/fichaje")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INICIO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:escribir")
    @DisplayName("POST /fichaje con 'tipo' ausente devuelve 400 (Bean Validation)")
    void registerTimeEntry_sinTipo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/fichaje")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/activo sin jornada abierta devuelve 204")
    void getActiveTimeEntry_sinJornadaAbierta_devuelve204() throws Exception {
        when(timeEntryService.getActiveTimeEntry("empleado@nxtime.test")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/fichaje/activo")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/historial devuelve 200 con la lista mapeada")
    void getHistory_conAuthority_devuelve200() throws Exception {
        when(timeEntryService.getHistory("empleado@nxtime.test")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/fichaje/historial")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/gestor/historial sin 'fichaje:leer:equipo' devuelve 403 (un EMPLEADO no ve al equipo)")
    void getTeamHistory_sinAuthorityDeEquipo_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/fichaje/gestor/historial")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "fichaje:leer:equipo")
    @DisplayName("GET /fichaje/gestor/historial con la authority de equipo devuelve 200")
    void getTeamHistory_conAuthorityDeEquipo_devuelve200() throws Exception {
        when(timeEntryService.getTeamHistory("gestor@nxtime.test")).thenReturn(List.<TeamTimeEntryDTO>of());

        mockMvc.perform(get("/api/v1/fichaje/gestor/historial")).andExpect(status().isOk());
        verify(timeEntryService).getTeamHistory("gestor@nxtime.test");
    }
}
