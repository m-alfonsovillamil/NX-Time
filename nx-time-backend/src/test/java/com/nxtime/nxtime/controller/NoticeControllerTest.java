package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.NoticeResponse;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.service.NoticeService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de NoticeController (Fase A).
 *
 * Los avisos no tienen authority propia: los tiene cualquiera que haya
 * iniciado sesión, y el caso que más importa comprobar es justamente
 * que un EMPLEADO -- el rol de abajo del todo -- entra sin problema.
 * Un "aviso:leer" que tuvieran los cuatro roles sería una restricción
 * de mentira.
 */
@NxTimeWebMvcTest(NoticeController.class)
@Import(WebMvcTestSecurityConfig.class)
class NoticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoticeService noticeService;

    private static NoticeResponse aviso() {
        return new NoticeResponse(
                1L, NoticeType.AUSENCIA_RESUELTA, "Tu ausencia ha sido aprobada",
                "VACACIONES, del 2026-06-01 al 2026-06-05", "ausencias", false, Instant.parse("2026-06-01T08:00:00Z"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /avisos como EMPLEADO devuelve 200: los avisos no piden authority")
    void getMyNotices_comoEmpleado_devuelve200() throws Exception {
        when(noticeService.getMisAvisos(any())).thenReturn(List.of(aviso()));

        mockMvc.perform(get("/api/v1/avisos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].tipo").value("AUSENCIA_RESUELTA"))
                .andExpect(jsonPath("$[0].rutaDestino").value("ausencias"))
                .andExpect(jsonPath("$[0].leido").value(false));
    }

    @Test
    @DisplayName("GET /avisos sin autenticar se rechaza y no llega al servicio")
    void getMyNotices_sinAutenticar_seRechaza() throws Exception {
        // Se comprueba el rechazo, no el código exacto: la cadena
        // mínima de WebMvcTestSecurityConfig no monta el punto de
        // entrada JWT, así que aquí sale un 403 donde producción
        // devuelve un 401. El código de verdad lo fija ApiContractTest
        // contra la aplicación completa.
        mockMvc.perform(get("/api/v1/avisos")).andExpect(status().is4xxClientError());

        verify(noticeService, Mockito.never()).getMisAvisos(any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /avisos/no-leidos devuelve el contador envuelto en un objeto, no un número suelto")
    void countUnread_devuelveElContador() throws Exception {
        when(noticeService.contarNoLeidos(any())).thenReturn(3L);

        mockMvc.perform(get("/api/v1/avisos/no-leidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidos").value(3));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("PATCH /avisos/{id}/leido marca el aviso indicado")
    void markAsRead_devuelve200() throws Exception {
        mockMvc.perform(patch("/api/v1/avisos/7/leido")).andExpect(status().isOk());

        verify(noticeService).marcarLeido(eq(7L), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("PATCH /avisos/{id}/leido sobre el aviso de otra persona devuelve 403")
    void markAsRead_avisoAjeno_devuelve403() throws Exception {
        Mockito.doThrow(new TenantAccessException("No puedes marcar avisos de otra persona."))
                .when(noticeService).marcarLeido(eq(9L), any());

        mockMvc.perform(patch("/api/v1/avisos/9/leido")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("PATCH /avisos/leer-todos no colisiona con la ruta de un aviso concreto")
    void markAllAsRead_devuelve200() throws Exception {
        mockMvc.perform(patch("/api/v1/avisos/leer-todos")).andExpect(status().isOk());

        verify(noticeService).marcarTodosLeidos(any());
        verify(noticeService, Mockito.never()).marcarLeido(Mockito.anyLong(), any());
    }
}
