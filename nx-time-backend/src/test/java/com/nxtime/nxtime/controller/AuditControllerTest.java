package com.nxtime.nxtime.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.dto.TimeEntryAuditResponse;
import com.nxtime.nxtime.mapper.TimeEntryAuditMapper;
import com.nxtime.nxtime.service.TimeEntryService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de AuditController: solo capa web (rutas,
 * authorities). El filtrado real por empresa lo cubre {@link
 * com.nxtime.nxtime.service.impl.TimeEntryServiceImplTest}.
 */
@NxTimeWebMvcTest(AuditController.class)
@Import(WebMvcTestSecurityConfig.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private TimeEntryAuditMapper auditMapper;

    @Test
    @WithMockUser(username = "rrhh@nxtime.test", authorities = "fichaje:auditoria")
    @DisplayName("GET /auditoria/fichaje/{id} con la authority correcta devuelve 200")
    void getAuditTrail_conAuthority_devuelve200() throws Exception {
        TimeEntryAudit auditRow = TimeEntryAudit.builder().id(1L).accion(AuditAction.CREACION).build();
        when(timeEntryService.getAuditTrail("rrhh@nxtime.test", 5L)).thenReturn(List.of(auditRow));
        when(auditMapper.toResponse(auditRow)).thenReturn(new TimeEntryAuditResponse(
                1L, 5L, null, null, AuditAction.CREACION, null, null, null, Instant.now(), null));

        mockMvc.perform(get("/api/v1/auditoria/fichaje/5")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "fichaje:leer:equipo")
    @DisplayName("GET /auditoria/fichaje/{id} sin la authority 'fichaje:auditoria' devuelve 403 (un GESTOR no la tiene)")
    void getAuditTrail_sinAuthority_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria/fichaje/5")).andExpect(status().isForbidden());
    }
}
