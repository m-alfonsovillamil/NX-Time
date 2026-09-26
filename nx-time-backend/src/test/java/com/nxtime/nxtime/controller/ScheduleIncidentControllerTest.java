package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.support.Paginas;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduleIncidentStatus;
import com.nxtime.nxtime.domain.ScheduleIncidentType;
import com.nxtime.nxtime.dto.ScheduleIncidentResponse;
import com.nxtime.nxtime.service.ScheduleIncidentService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de ScheduleIncidentController (Fase B2).
 *
 * Lo que se fija es el reparto: ver y explicar las propias lo puede todo el
 * mundo —son tuyas—; la bandeja del equipo y decidir empiezan en GESTOR. Que
 * nadie decida sobre las suyas, aunque tenga el permiso, lo corta el servicio
 * y lo prueba IncidenciasIT.
 */
@NxTimeWebMvcTest(ScheduleIncidentController.class)
@Import(WebMvcTestSecurityConfig.class)
class ScheduleIncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleIncidentService incidentService;

    private static ScheduleIncidentResponse incidencia(ScheduleIncidentStatus estado) {
        return new ScheduleIncidentResponse(7L, 10L, "Ana Prueba", LocalDate.of(2026, 10, 5),
                ScheduleIncidentType.RETRASO, 25, "09:00", null, estado, null, null, null, null, null);
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /incidencias/mias: un EMPLEADO ve las suyas, con la hora prevista")
    void mias_comoEmpleado() throws Exception {
        when(incidentService.mias(any(), anyInt())).thenReturn(List.of(incidencia(ScheduleIncidentStatus.PENDIENTE)));

        mockMvc.perform(get("/api/v1/incidencias/mias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RETRASO"))
                .andExpect(jsonPath("$[0].horaPrevista").value("09:00"))
                .andExpect(jsonPath("$[0].minutos").value(25));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /incidencias/{id}/justificacion: un EMPLEADO explica la suya")
    void justificar_comoEmpleado() throws Exception {
        when(incidentService.justificar(eq(7L), eq("Avería del metro"), any()))
                .thenReturn(incidencia(ScheduleIncidentStatus.JUSTIFICADA));

        mockMvc.perform(post("/api/v1/incidencias/7/justificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Avería del metro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("JUSTIFICADA"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Una explicación vacía es un 400 y no llega al servicio")
    void justificar_vacia() throws Exception {
        mockMvc.perform(post("/api/v1/incidencias/7/justificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verify(incidentService, never()).justificar(anyLong(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Un EMPLEADO no ve la bandeja del equipo ni decide")
    void empleado_noRevisa() throws Exception {
        mockMvc.perform(get("/api/v1/incidencias/equipo")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/incidencias/7/resolucion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceptar\":true}"))
                .andExpect(status().isForbidden());
        verify(incidentService, never()).bandeja(any(), anyBoolean(), any());
        verify(incidentService, never()).resolver(anyLong(), anyBoolean(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un GESTOR ve la bandeja, por defecto la que espera decisión")
    void bandeja_comoGestor() throws Exception {
        when(incidentService.bandeja(any(), eq(false), any()))
                .thenReturn(Paginas.una(List.of(incidencia(ScheduleIncidentStatus.JUSTIFICADA))));

        mockMvc.perform(get("/api/v1/incidencias/equipo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].usuario").value("Ana Prueba"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un GESTOR decide, y la decisión llega tal cual al servicio")
    void resolver_comoGestor() throws Exception {
        when(incidentService.resolver(eq(7L), eq(false), eq("Tercera vez"), any()))
                .thenReturn(incidencia(ScheduleIncidentStatus.RECHAZADA));

        mockMvc.perform(post("/api/v1/incidencias/7/resolucion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceptar\":false,\"comentario\":\"Tercera vez\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Decidir sin decir si se acepta es un 400")
    void resolver_sinDecision() throws Exception {
        mockMvc.perform(post("/api/v1/incidencias/7/resolucion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comentario\":\"Hmm\"}"))
                .andExpect(status().isBadRequest());
        verify(incidentService, never()).resolver(anyLong(), anyBoolean(), any(), any());
    }
}
