package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Unitarios (Mockito) de la máquina de estados del fichaje: los 4
 * caminos de {@link TimeEntryAction} y sus errores de negocio, más el
 * cálculo de segundos de pausa acumulados (ver auditoría: el bug
 * original truncaba minutos por cada pausa individual en vez de
 * acumular segundos y derivar los minutos una sola vez al final).
 */
@ExtendWith(MockitoExtension.class)
class TimeEntryServiceImplTest {

    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TimeEntryMapper timeEntryMapper;

    private TimeEntryServiceImpl service;

    private User empleado;
    private Company empresa;

    @BeforeEach
    void setUp() {
        service = new TimeEntryServiceImpl(timeEntryRepository, userRepository, timeEntryMapper);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").nombre("Empleado").empresa(empresa).build();
        // lenient: no todos los tests llegan a guardar (varios cortan antes con una excepción de negocio).
        lenient().when(timeEntryRepository.save(any(TimeEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- INICIO ----

    @Test
    @DisplayName("INICIO sin jornada activa crea un fichaje nuevo con horaEntrada")
    void registerTimeEntry_inicioSinJornadaActiva_creaFichaje() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        TimeEntry result = service.registerTimeEntry(empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.INICIO));

        assertThat(result.getUsuario()).isEqualTo(empleado);
        assertThat(result.getEmpresa()).isEqualTo(empresa);
        assertThat(result.getHoraEntrada()).isNotNull();
        assertThat(result.getHoraSalida()).isNull();
        verify(timeEntryRepository).save(any(TimeEntry.class));
    }

    @Test
    @DisplayName("INICIO con una jornada ya activa lanza BusinessException")
    void registerTimeEntry_inicioConJornadaActiva_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado).horaEntrada(Instant.now()).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        assertThatThrownBy(() ->
                        service.registerTimeEntry(empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.INICIO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya hay una jornada activa");
        verify(timeEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fichar contra un usuario inexistente lanza ResourceNotFoundException")
    void registerTimeEntry_usuarioNoExiste_lanzaResourceNotFoundException() {
        when(userRepository.findByEmail("fantasma@nxtime.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.registerTimeEntry("fantasma@nxtime.test", new TimeEntryRequest(TimeEntryAction.INICIO)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- FIN ----

    @Test
    @DisplayName("FIN con jornada activa (sin pausa) la cierra con horaSalida")
    void registerTimeEntry_finConJornadaActiva_cierraJornada() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado)
                .horaEntrada(Instant.now().minusSeconds(3600)).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        TimeEntry result = service.registerTimeEntry(empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.FIN));

        assertThat(result.getHoraSalida()).isNotNull();
    }

    @Test
    @DisplayName("FIN sin jornada activa lanza BusinessException")
    void registerTimeEntry_finSinJornadaActiva_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.registerTimeEntry(empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.FIN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No hay jornada activa para finalizar");
    }

    @Test
    @DisplayName("FIN mientras está en pausa lanza BusinessException (hay que reanudar antes)")
    void registerTimeEntry_finEnPausa_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado).horaEntrada(Instant.now())
                .enPausa(true).inicioPausaActual(Instant.now()).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        assertThatThrownBy(() ->
                        service.registerTimeEntry(empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.FIN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pausa");
    }

    // ---- PAUSA_INICIO ----

    @Test
    @DisplayName("PAUSA_INICIO con jornada activa la pone en pausa")
    void registerTimeEntry_pausaInicioConJornadaActiva_ponEnPausa() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado).horaEntrada(Instant.now()).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        TimeEntry result = service.registerTimeEntry(
                empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.PAUSA_INICIO));

        assertThat(result.isEnPausa()).isTrue();
        assertThat(result.getInicioPausaActual()).isNotNull();
    }

    @Test
    @DisplayName("PAUSA_INICIO sin jornada activa lanza BusinessException")
    void registerTimeEntry_pausaInicioSinJornadaActiva_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerTimeEntry(
                        empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.PAUSA_INICIO)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PAUSA_INICIO ya en pausa lanza BusinessException")
    void registerTimeEntry_pausaInicioYaEnPausa_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado).horaEntrada(Instant.now())
                .enPausa(true).inicioPausaActual(Instant.now()).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        assertThatThrownBy(() -> service.registerTimeEntry(
                        empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.PAUSA_INICIO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya está en pausa");
    }

    // ---- PAUSA_FIN ----

    @Test
    @DisplayName("PAUSA_FIN acumula los segundos exactos de la pausa, sin truncar a minutos")
    void registerTimeEntry_pausaFin_acumulaSegundosSinTruncar() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        Instant inicioPausa = Instant.now().minus(90, ChronoUnit.SECONDS);
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado)
                .horaEntrada(Instant.now().minusSeconds(3600))
                .enPausa(true).inicioPausaActual(inicioPausa).segundosPausaAcumulados(30L).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        TimeEntry result = service.registerTimeEntry(
                empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.PAUSA_FIN));

        assertThat(result.isEnPausa()).isFalse();
        assertThat(result.getInicioPausaActual()).isNull();
        // 30s ya acumulados + ~90s de esta pausa (margen por el tiempo real de ejecución del test).
        assertThat(result.getSegundosPausaAcumulados()).isBetween(119L, 125L);
    }

    @Test
    @DisplayName("PAUSA_FIN sin jornada activa lanza BusinessException")
    void registerTimeEntry_pausaFinSinJornadaActiva_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerTimeEntry(
                        empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.PAUSA_FIN)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PAUSA_FIN sin estar en pausa lanza BusinessException")
    void registerTimeEntry_pausaFinSinEstarEnPausa_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado).horaEntrada(Instant.now()).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        assertThatThrownBy(() -> service.registerTimeEntry(
                        empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.PAUSA_FIN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no está en pausa");
    }

    @Test
    @DisplayName("PAUSA_FIN con enPausa=true pero sin inicioPausaActual es una invariante rota (500 genérico)")
    void registerTimeEntry_pausaFinConInvarianteRota_lanzaIllegalStateException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado).horaEntrada(Instant.now())
                .enPausa(true).inicioPausaActual(null).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        assertThatThrownBy(() -> service.registerTimeEntry(
                        empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.PAUSA_FIN)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- Consultas ----

    @Test
    @DisplayName("getActiveTimeEntry devuelve vacío si no hay jornada abierta")
    void getActiveTimeEntry_sinJornadaAbierta_devuelveOptionalVacio() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        assertThat(service.getActiveTimeEntry(empleado.getEmail())).isEmpty();
    }

    @Test
    @DisplayName("getHistory delega en el repositorio con un Pageable acotado a 200 filas")
    void getHistory_delegaEnRepositorioConPageableLimitado() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry entry = TimeEntry.builder().id(1L).usuario(empleado).horaEntrada(Instant.now()).build();
        when(timeEntryRepository.findHistoryByUsuario(eq(empleado), any(Pageable.class))).thenReturn(List.of(entry));

        List<TimeEntry> result = service.getHistory(empleado.getEmail());

        assertThat(result).containsExactly(entry);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(timeEntryRepository).findHistoryByUsuario(eq(empleado), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    @DisplayName("getTeamHistory mapea a TeamTimeEntryDTO los fichajes de la empresa del gestor")
    void getTeamHistory_mapeaFichajesDeLaEmpresaDelGestor() {
        User gestor = User.builder().id(20L).email("gestor@nxtime.test").empresa(empresa).build();
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        TimeEntry entry = TimeEntry.builder().id(1L).usuario(empleado).empresa(empresa).horaEntrada(Instant.now()).build();
        when(timeEntryRepository.findTeamHistory(eq(empresa), any(Pageable.class))).thenReturn(List.of(entry));
        TeamTimeEntryDTO dto = new TeamTimeEntryDTO(1L, Instant.now(), null, null, null, 0L);
        when(timeEntryMapper.toTeamDTO(entry)).thenReturn(dto);

        List<TeamTimeEntryDTO> result = service.getTeamHistory(gestor.getEmail());

        assertThat(result).containsExactly(dto);
    }
}
