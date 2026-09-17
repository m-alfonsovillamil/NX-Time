package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.OvertimeAlertResponse;
import com.nxtime.nxtime.dto.PendingWorkResponse;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.service.CorrectionService;
import com.nxtime.nxtime.service.DataDeletionService;
import com.nxtime.nxtime.service.OvertimeService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los contadores del panel de gestión. Lo que importa es que cada uno diga
 * lo mismo que su bandeja, y eso se consigue contando con los mismos métodos.
 */
class PendingWorkServiceImplTest {

    private final AbsenceService absenceService = mock(AbsenceService.class);
    private final CorrectionService correctionService = mock(CorrectionService.class);
    private final OvertimeService overtimeService = mock(OvertimeService.class);
    private final DataDeletionService dataDeletionService = mock(DataDeletionService.class);

    /*
     * 31 de diciembre a las 23:30 UTC, que en Madrid ya es 1 de enero: el año
     * de las horas extra tiene que ser el de Madrid, como en la bandeja.
     */
    private final Clock nochevieja = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.UTC);

    private PendingWorkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PendingWorkServiceImpl(
                absenceService, correctionService, overtimeService, dataDeletionService, nochevieja);
    }

    private static User con(Role rol) {
        return User.builder().id(1L).email("alguien@test").nombre("Alguien").rol(rol).build();
    }

    private static OvertimeAlertResponse aviso(String estado) {
        return new OvertimeAlertResponse(1L, 2L, "Ana", "DIARIA", LocalDate.of(2027, 1, 1), null,
                60, 540, null, estado, null, null, null);
    }

    @Test
    @DisplayName("Cada contador es el tamaño de lo que devuelve su bandeja")
    void cuentaLoMismoQueLasBandejas() {
        User gestora = con(Role.GESTOR);
        when(absenceService.getPendingRequests("alguien@test"))
                .thenReturn(List.of(mock(AbsenceResponse.class), mock(AbsenceResponse.class)));
        when(correctionService.pendientesParaMi(gestora)).thenReturn(List.of(mock(CorrectionResponse.class)));
        when(overtimeService.delEquipo(eq(gestora), anyInt())).thenReturn(List.of(aviso("ABIERTO")));

        PendingWorkResponse contadores = service.contar(gestora);

        assertThat(contadores).isEqualTo(new PendingWorkResponse(2, 1, 1, 0));
    }

    /*
     * La bandeja de horas extra enseña TODOS los avisos del año, también los
     * ya decididos. El contador dice lo que espera una decisión: solo ABIERTO.
     */
    @Test
    @DisplayName("Las horas extra ya aceptadas o justificadas no cuentan como pendientes")
    void horasExtra_soloLasAbiertas() {
        User gestora = con(Role.GESTOR);
        when(overtimeService.delEquipo(eq(gestora), anyInt()))
                .thenReturn(List.of(aviso("ABIERTO"), aviso("ACEPTADO"), aviso("JUSTIFICADO"), aviso("ABIERTO")));

        assertThat(service.contar(gestora).horasExtra()).isEqualTo(2);
    }

    @Test
    @DisplayName("El año de las horas extra es el de Madrid, no el de UTC")
    void horasExtra_anioDeMadrid() {
        User gestora = con(Role.GESTOR);

        service.contar(gestora);

        verify(overtimeService).delEquipo(gestora, 2027);
    }

    /*
     * Sin la authority de una bandeja, 0 y sin llamar a su servicio: el panel
     * pide los tres contadores juntos, y uno sin permiso no debe tumbar los otros.
     */
    @Test
    @DisplayName("Sin permiso para una bandeja su contador es 0, y no se llega a consultar")
    void sinPermiso_ceroSinConsultar() {
        User empleado = con(Role.EMPLEADO);
        when(correctionService.pendientesParaMi(any())).thenReturn(List.of());

        assertThat(service.contar(empleado)).isEqualTo(new PendingWorkResponse(0, 0, 0, 0));
        verify(absenceService, never()).getPendingRequests(anyString());
        verify(overtimeService, never()).delEquipo(any(), anyInt());
        verify(dataDeletionService, never()).contarPendientes(any());
    }

    /*
     * Los borrados son de RRHH y ADMIN, no de GESTOR: un gestor no debe ver
     * que alguien de la empresa ha pedido que se borren sus datos.
     */
    @Test
    @DisplayName("Los borrados pendientes cuentan para RRHH y no para un GESTOR")
    void borrados_soloParaQuienLosEjecuta() {
        User rrhh = con(Role.RRHH);
        User gestora = con(Role.GESTOR);
        when(dataDeletionService.contarPendientes(any())).thenReturn(3);

        assertThat(service.contar(rrhh).borrados()).isEqualTo(3);
        assertThat(service.contar(gestora).borrados()).isZero();
    }
}
