package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.DeletionResponse;
import com.nxtime.nxtime.service.DataDeletionService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de DataDeletionController (ADR 016).
 *
 * El reparto: <b>pedir lo tuyo no pide nada; ver y ejecutar los de otros,
 * {@code empleado:gestionar}</b>, que tienen RRHH y ADMIN pero no GESTOR. Un
 * gestor no tiene por qué saber que alguien de su equipo ha pedido que se
 * borren sus datos.
 */
@NxTimeWebMvcTest(DataDeletionController.class)
@Import(WebMvcTestSecurityConfig.class)
class DataDeletionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataDeletionService service;

    private DeletionResponse solicitud(String estado) {
        return new DeletionResponse(3L, 10L, "Ana Pruebas", "ana@test", estado, null, null,
                Instant.parse("2026-09-17T08:00:00Z"), null, null, null, null, List.of());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /perfil/borrado: un EMPLEADO lo pide, sin cuerpo, y recibe 201")
    void solicitar_comoEmpleado_devuelve201() throws Exception {
        when(service.solicitar(any(), any())).thenReturn(solicitud("PENDIENTE"));

        mockMvc.perform(post("/api/v1/perfil/borrado"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /perfil/borrado sin ninguna pedida devuelve 204")
    void miSolicitud_sinNinguna_devuelve204() throws Exception {
        when(service.miUltimaSolicitud(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/perfil/borrado")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("La bandeja y ejecutar como GESTOR devuelven 403 sin llegar al servicio")
    void bandeja_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/borrados/pendientes")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/borrados/3/ejecutar")).andExpect(status().isForbidden());
        verify(service, never()).pendientes(any());
        verify(service, never()).ejecutar(anyLong(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Registrar y ver candidatos como GESTOR devuelven 403")
    void registrar_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/borrados/candidatos")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/borrados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":10,\"motivo\":\"Correo\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).registrar(any(), anyLong(), anyString());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("POST /borrados sin decir cómo llegó devuelve 400 sin llegar al servicio")
    void registrar_sinMotivo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/borrados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":10,\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).registrar(any(), anyLong(), anyString());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("POST /borrados/{id}/ejecutar como RRHH llega al servicio")
    void ejecutar_comoRrhh_devuelve200() throws Exception {
        when(service.ejecutar(eq(3L), any())).thenReturn(solicitud("EJECUTADA"));

        mockMvc.perform(post("/api/v1/borrados/3/ejecutar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EJECUTADA"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.ADMIN)
    @DisplayName("POST /borrados/{id}/rechazar sin comentario devuelve 400 sin llegar al servicio")
    void rechazar_sinComentario_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/borrados/3/rechazar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comentario\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).rechazar(anyLong(), anyString(), any());
    }
}
