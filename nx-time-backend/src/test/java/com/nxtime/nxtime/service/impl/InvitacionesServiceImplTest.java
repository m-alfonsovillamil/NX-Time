package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.AccessCodeType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.InvitacionPendiente;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.AccessCodeRepository;
import com.nxtime.nxtime.repository.InvitacionPendienteRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AccessCodeService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

/** El cupo diario de invitaciones y cómo se manda una tanda (ADR 052). */
class InvitacionesServiceImplTest {

    private final InvitacionPendienteRepository pendientes = mock(InvitacionPendienteRepository.class);
    private final AccessCodeRepository codigos = mock(AccessCodeRepository.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private final AccessCodeService accessCodeService = mock(AccessCodeService.class);
    private InvitacionesServiceImpl servicio;
    private final Company empresa = Company.builder().id(7L).nombre("Empresa").build();

    @BeforeEach
    void setUp() {
        servicio = new InvitacionesServiceImpl(pendientes, codigos, usuarios, accessCodeService, Runnable::run,
                mock(PlatformTransactionManager.class), 250,
                Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("El cupo de hoy es lo menor entre lo que le queda a la empresa y lo que le queda a la instalación")
    void cupo() {
        when(codigos.countByUsuario_Empresa_IdAndTipoAndCreadoEnAfter(eq(7L), eq(AccessCodeType.ALTA), any()))
                .thenReturn(10L);
        when(codigos.countByTipoAndCreadoEnAfter(eq(AccessCodeType.ALTA), any())).thenReturn(0L);
        assertThat(servicio.cupoDeHoy(7L)).isEqualTo(40);

        when(codigos.countByTipoAndCreadoEnAfter(eq(AccessCodeType.ALTA), any())).thenReturn(230L);
        assertThat(servicio.cupoDeHoy(7L)).isEqualTo(20);

        when(codigos.countByTipoAndCreadoEnAfter(eq(AccessCodeType.ALTA), any())).thenReturn(300L);
        assertThat(servicio.cupoDeHoy(7L)).isZero();
    }

    private User persona(long id, boolean activo) {
        return User.builder().id(id).nombre("P" + id).email("p" + id + "@empresa.test").rol(Role.EMPLEADO)
                .empresa(empresa).activo(activo).build();
    }

    private void enCola(long... ids) {
        for (long id : ids) {
            when(pendientes.findById(id)).thenReturn(Optional.of(
                    InvitacionPendiente.builder().usuarioId(id).empresaId(7L).build()));
        }
    }

    @Test
    @DisplayName("Si el correo no sale, la tanda para ahí y el fallo queda apuntado; las de antes sí han salido")
    void paraAlPrimerFallo() {
        enCola(1, 2, 3);
        when(usuarios.findById(1L)).thenReturn(Optional.of(persona(1, true)));
        when(usuarios.findById(2L)).thenReturn(Optional.of(persona(2, true)));
        when(usuarios.findById(3L)).thenReturn(Optional.of(persona(3, true)));
        doThrow(new BusinessException("No se ha podido enviar el correo", HttpStatus.SERVICE_UNAVAILABLE))
                .when(accessCodeService).emitirCodigoDeAlta(eq(persona(2, true)), anyString());

        servicio.mandar(7L, List.of(1L, 2L, 3L));

        verify(accessCodeService).emitirCodigoDeAlta(eq(persona(1, true)), anyString());
        verify(accessCodeService, never()).emitirCodigoDeAlta(eq(persona(3, true)), anyString());
        verify(pendientes, times(1)).delete(any());
        verify(pendientes).save(org.mockito.ArgumentMatchers.argThat(
                fila -> fila.getUsuarioId() == 2L && fila.getIntentos() == 1 && fila.getUltimoError().contains("correo")));
    }

    @Test
    @DisplayName("A quien se dio de baja antes de que le llegara no se le manda, y sale de la cola")
    void deBajaNoSeInvita() {
        enCola(5);
        when(usuarios.findById(5L)).thenReturn(Optional.of(persona(5, false)));

        servicio.mandar(7L, List.of(5L));

        verify(accessCodeService, never()).emitirCodigoDeAlta(any(), anyString());
        verify(pendientes).delete(any());
    }

    @Test
    @DisplayName("Sin cupo no se empieza ninguna tanda")
    void sinCupoNoSeEmpieza() {
        when(pendientes.countByEmpresaId(7L)).thenReturn(5L);
        when(codigos.countByUsuario_Empresa_IdAndTipoAndCreadoEnAfter(anyLong(), any(), any())).thenReturn(50L);
        User admin = persona(1, true);

        var estado = servicio.enviarPendientes(admin);

        assertThat(estado.enCamino()).isZero();
        assertThat(estado.pendientes()).isEqualTo(5);
        verify(pendientes, never()).findByEmpresaIdOrderByCreadaEnAscUsuarioIdAsc(anyLong(), any());
    }
}
