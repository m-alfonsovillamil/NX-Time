package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.OvertimeAlertResponse;
import com.nxtime.nxtime.dto.OvertimeBalanceResponse;
import com.nxtime.nxtime.dto.ReviewOvertimeRequest;
import com.nxtime.nxtime.service.OvertimeService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de OvertimeController (Fase F).
 *
 * Lo que se fija aquí es el reparto de permisos, que en esta fase no es
 * simétrico: <b>ver lo tuyo no pide authority; revisar, sí</b>. Mirar tus
 * propias horas extra es mirar tu propia jornada — de hecho el aviso te
 * llega a ti antes que a nadie — mientras que decidir si cuentan es una
 * operación de gestión.
 *
 * Lo que este test NO cubre, a propósito, es que nadie revise lo suyo
 * propio: eso no lo puede parar un {@code @PreAuthorize}, porque quien
 * revisa tiene la authority precisamente. Vive en el servicio y se prueba
 * en {@code OvertimeServiceIT}.
 */
@NxTimeWebMvcTest(OvertimeController.class)
@Import(WebMvcTestSecurityConfig.class)
class OvertimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OvertimeService overtimeService;

    private OvertimeAlertResponse aviso() {
        return new OvertimeAlertResponse(
                7L, 10L, "Ana", "DIARIA",
                LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 3),
                120, 540, 42L, "ABIERTO", null, null, null);
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /horas-extra: un EMPLEADO ve las suyas sin necesitar nada más")
    void mios_comoEmpleado_devuelve200() throws Exception {
        when(overtimeService.mios(any(), anyInt())).thenReturn(List.of(aviso()));

        mockMvc.perform(get("/api/v1/horas-extra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].minutosExtra").value(120))
                .andExpect(jsonPath("$[0].minutosEsperados").value(540))
                .andExpect(jsonPath("$[0].estado").value("ABIERTO"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /horas-extra/equipo como EMPLEADO devuelve 403")
    void delEquipo_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/horas-extra/equipo")).andExpect(status().isForbidden());
        verify(overtimeService, never()).delEquipo(any(), anyInt());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /horas-extra/equipo con 'horasextra:revisar' (GESTOR) devuelve 200")
    void delEquipo_comoGestor_devuelve200() throws Exception {
        when(overtimeService.delEquipo(any(), anyInt())).thenReturn(List.of(aviso()));

        mockMvc.perform(get("/api/v1/horas-extra/equipo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuario").value("Ana"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("PATCH /horas-extra/{id} como EMPLEADO devuelve 403")
    void revisar_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/horas-extra/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceptar\":true}"))
                .andExpect(status().isForbidden());
        verify(overtimeService, never()).revisar(anyInt(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PATCH /horas-extra/{id} con la authority llega al servicio con la decisión")
    void revisar_comoGestor_devuelve200() throws Exception {
        when(overtimeService.revisar(eq(7L), any(), any())).thenReturn(aviso());

        mockMvc.perform(patch("/api/v1/horas-extra/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceptar\":false,\"justificacion\":\"Intensiva pactada\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReviewOvertimeRequest> captor =
                ArgumentCaptor.forClass(ReviewOvertimeRequest.class);
        verify(overtimeService).revisar(eq(7L), captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().aceptar()).isFalse();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().justificacion())
                .isEqualTo("Intensiva pactada");
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PATCH /horas-extra/{id} sin 'aceptar' devuelve 400")
    void revisar_sinDecision_devuelve400() throws Exception {
        // El campo es Boolean y no boolean justamente para esto: con el
        // primitivo, un cuerpo sin "aceptar" se deserializaría a false y
        // el aviso se archivaría sin que nadie lo hubiera pedido.
        mockMvc.perform(patch("/api/v1/horas-extra/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificacion\":\"Algo\"}"))
                .andExpect(status().isBadRequest());
        verify(overtimeService, never()).revisar(anyInt(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /horas-extra/bolsa: la propia se ve sin permisos de gestión")
    void bolsaPropia_comoEmpleado_devuelve200() throws Exception {
        when(overtimeService.bolsa(any(), any(), anyInt()))
                .thenReturn(new OvertimeBalanceResponse(2026, 4800, 3840, 960, 2, true));

        mockMvc.perform(get("/api/v1/horas-extra/bolsa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutosTope").value(4800))
                .andExpect(jsonPath("$.minutosDisponibles").value(960))
                .andExpect(jsonPath("$.alLimite").value(true));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /horas-extra sin 'anio' usa el año en curso, no un cero")
    void sinAnio_usaElAnioEnCurso() throws Exception {
        when(overtimeService.mios(any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/horas-extra")).andExpect(status().isOk());

        verify(overtimeService).mios(any(),
                eq(LocalDate.now(java.time.ZoneId.of("Europe/Madrid")).getYear()));
    }
}
