package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryCorrectionRequest;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private TimeEntryAuditRepository timeEntryAuditRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TimeEntryMapper timeEntryMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TimeEntryServiceImpl service;

    private User empleado;
    private Company empresa;

    @BeforeEach
    void setUp() {
        // ObjectMapper real (no mockeado): construir a mano el JSON
        // esperado para cada snapshot sería tan frágil como el propio
        // código a probar. JavaTimeModule hace falta a mano aquí porque,
        // a diferencia del ObjectMapper que autoconfigura Spring Boot en
        // la app real, uno construido con "new" en un test no lo trae
        // registrado por defecto -- y los snapshots serializan Instant.
        var snapshotSerializer = new TimeEntrySnapshotSerializer(
                new ObjectMapper().registerModule(new JavaTimeModule()));
        service = new TimeEntryServiceImpl(
                timeEntryRepository, timeEntryAuditRepository, userRepository, timeEntryMapper,
                eventPublisher, snapshotSerializer);
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
        TeamTimeEntryDTO dto = new TeamTimeEntryDTO(1L, Instant.now(), null, null, null, 0L, 0L);
        when(timeEntryMapper.toTeamDTO(entry)).thenReturn(dto);

        List<TeamTimeEntryDTO> result = service.getTeamHistory(gestor.getEmail());

        assertThat(result).containsExactly(dto);
    }

    // ---- Auditoría (Fase 8): publicación de eventos ----

    @Test
    @DisplayName("INICIO publica un evento de auditoría CREACION sin valorAnterior")
    void registerTimeEntry_inicio_publicaEventoDeCreacion() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        service.registerTimeEntry(empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.INICIO));

        ArgumentCaptor<TimeEntryAuditEvent> captor = ArgumentCaptor.forClass(TimeEntryAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var auditRow = captor.getValue().auditRow();
        assertThat(auditRow.getAccion()).isEqualTo(AuditAction.CREACION);
        assertThat(auditRow.getValorAnterior()).isNull();
        assertThat(auditRow.getValorNuevo()).contains("\"horaEntrada\"");
        assertThat(auditRow.getUsuario()).isEqualTo(empleado);
        assertThat(auditRow.getModificadoPor()).isEqualTo(empleado);
    }

    @Test
    @DisplayName("FIN publica un evento de auditoría MODIFICACION con valorAnterior y valorNuevo")
    void registerTimeEntry_fin_publicaEventoDeModificacionConAmbosValores() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        TimeEntry activa = TimeEntry.builder().id(1L).usuario(empleado)
                .horaEntrada(Instant.now().minusSeconds(3600)).build();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.of(activa));

        service.registerTimeEntry(empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.FIN));

        ArgumentCaptor<TimeEntryAuditEvent> captor = ArgumentCaptor.forClass(TimeEntryAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var auditRow = captor.getValue().auditRow();
        assertThat(auditRow.getAccion()).isEqualTo(AuditAction.MODIFICACION);
        assertThat(auditRow.getValorAnterior()).contains("\"horaSalida\":null");
        assertThat(auditRow.getValorNuevo()).doesNotContain("\"horaSalida\":null");
    }

    // ---- correctTimeEntry (Fase 8) ----

    @Test
    @DisplayName("correctTimeEntry anula el original, crea uno nuevo enlazado y publica un evento CORRECCION")
    void correctTimeEntry_fichajeValido_creaCorreccionYPublicaEvento() {
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        Instant horaEntrada = Instant.now().minusSeconds(7200);
        Instant horaSalida = Instant.now().minusSeconds(3600);
        TimeEntry original = TimeEntry.builder().id(5L).usuario(empleado).empresa(empresa)
                .horaEntrada(horaEntrada).horaSalida(horaSalida).build();
        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(original));

        Instant horaEntradaCorregida = horaEntrada.minusSeconds(600);
        TimeEntryCorrectionRequest request =
                new TimeEntryCorrectionRequest(horaEntradaCorregida, horaSalida, "Se le olvidó fichar la entrada a tiempo.");

        TimeEntry result = service.correctTimeEntry(rrhh.getEmail(), 5L, request);

        assertThat(result.getHoraEntrada()).isEqualTo(horaEntradaCorregida);
        assertThat(result.getRegistroOriginal()).isEqualTo(original);
        assertThat(original.isAnulado()).isTrue();

        ArgumentCaptor<TimeEntryAuditEvent> captor = ArgumentCaptor.forClass(TimeEntryAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var auditRow = captor.getValue().auditRow();
        assertThat(auditRow.getAccion()).isEqualTo(AuditAction.CORRECCION);
        assertThat(auditRow.getModificadoPor()).isEqualTo(rrhh);
        assertThat(auditRow.getMotivo()).isEqualTo(request.motivo());
    }

    @Test
    @DisplayName("correctTimeEntry sobre un fichaje de OTRA empresa lanza TenantAccessException")
    void correctTimeEntry_fichajeDeOtraEmpresa_lanzaTenantAccessException() {
        Company otraEmpresa = Company.builder().id(2L).build();
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        TimeEntry original = TimeEntry.builder().id(5L).usuario(empleado).empresa(otraEmpresa)
                .horaEntrada(Instant.now().minusSeconds(7200)).horaSalida(Instant.now().minusSeconds(3600)).build();
        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(original));

        TimeEntryCorrectionRequest request = new TimeEntryCorrectionRequest(Instant.now(), Instant.now(), "motivo");

        assertThatThrownBy(() -> service.correctTimeEntry(rrhh.getEmail(), 5L, request))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("correctTimeEntry sobre un fichaje ya corregido antes lanza BusinessException")
    void correctTimeEntry_fichajeYaAnulado_lanzaBusinessException() {
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        TimeEntry original = TimeEntry.builder().id(5L).usuario(empleado).empresa(empresa)
                .horaEntrada(Instant.now().minusSeconds(7200)).horaSalida(Instant.now().minusSeconds(3600))
                .anulado(true).build();
        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(original));

        TimeEntryCorrectionRequest request = new TimeEntryCorrectionRequest(Instant.now(), Instant.now(), "motivo");

        assertThatThrownBy(() -> service.correctTimeEntry(rrhh.getEmail(), 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya fue corregido");
    }

    @Test
    @DisplayName("correctTimeEntry sobre una jornada activa (sin horaSalida) lanza BusinessException")
    void correctTimeEntry_jornadaActiva_lanzaBusinessException() {
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        TimeEntry original = TimeEntry.builder().id(5L).usuario(empleado).empresa(empresa)
                .horaEntrada(Instant.now()).build();
        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(original));

        TimeEntryCorrectionRequest request = new TimeEntryCorrectionRequest(Instant.now(), Instant.now(), "motivo");

        assertThatThrownBy(() -> service.correctTimeEntry(rrhh.getEmail(), 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("activa");
    }

    @Test
    @DisplayName("correctTimeEntry con horaSalida no posterior a horaEntrada lanza BusinessException 400")
    void correctTimeEntry_horaSalidaNoPosterior_lanzaBusinessException() {
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        Instant hora = Instant.now();
        TimeEntry original = TimeEntry.builder().id(5L).usuario(empleado).empresa(empresa)
                .horaEntrada(hora.minusSeconds(7200)).horaSalida(hora.minusSeconds(3600)).build();
        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(original));

        TimeEntryCorrectionRequest request = new TimeEntryCorrectionRequest(hora, hora, "motivo");

        assertThatThrownBy(() -> service.correctTimeEntry(rrhh.getEmail(), 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("posterior");
    }

    // ---- getAuditTrail (Fase 8) ----

    @Test
    @DisplayName("getAuditTrail devuelve la línea temporal de un fichaje de la propia empresa")
    void getAuditTrail_fichajeDeLaPropiaEmpresa_devuelveLineaTemporal() {
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        TimeEntry entry = TimeEntry.builder().id(5L).usuario(empleado).empresa(empresa).build();
        var auditRow = com.nxtime.nxtime.domain.TimeEntryAudit.builder().id(1L).registro(entry).build();
        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(entry));
        when(timeEntryRepository.findByRegistroOriginal_Id(5L)).thenReturn(Optional.empty());
        when(timeEntryAuditRepository.findByRegistro_IdInOrderByFechaHoraAsc(List.of(5L)))
                .thenReturn(List.of(auditRow));

        assertThat(service.getAuditTrail(rrhh.getEmail(), 5L)).containsExactly(auditRow);
    }

    @Test
    @DisplayName("Pedir la auditoría del fichaje CORREGIDO devuelve también la del original que sustituye")
    void getAuditTrail_fichajeCorregido_incluyeLaHistoriaDelOriginal() {
        // Una corrección no sobrescribe: anula el original y crea uno
        // nuevo. La traza se queda bajo el id ANULADO, que es el que el
        // historial oculta -- así que preguntar por el fichaje válido,
        // el único visible, devolvía una lista vacía.
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        TimeEntry original = TimeEntry.builder().id(5L).usuario(empleado).empresa(empresa).anulado(true).build();
        TimeEntry correccion = TimeEntry.builder().id(6L).usuario(empleado).empresa(empresa)
                .registroOriginal(original).build();
        var filaCreacion = com.nxtime.nxtime.domain.TimeEntryAudit.builder().id(1L).registro(original).build();
        var filaCorreccion = com.nxtime.nxtime.domain.TimeEntryAudit.builder().id(2L).registro(original).build();

        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(6L)).thenReturn(Optional.of(correccion));
        when(timeEntryRepository.findByRegistroOriginal_Id(5L)).thenReturn(Optional.of(correccion));
        when(timeEntryRepository.findByRegistroOriginal_Id(6L)).thenReturn(Optional.empty());
        when(timeEntryAuditRepository.findByRegistro_IdInOrderByFechaHoraAsc(List.of(5L, 6L)))
                .thenReturn(List.of(filaCreacion, filaCorreccion));

        assertThat(service.getAuditTrail(rrhh.getEmail(), 6L))
                .containsExactly(filaCreacion, filaCorreccion);
    }

    @Test
    @DisplayName("La cadena se recorre entera aunque se pregunte por el fichaje del medio")
    void getAuditTrail_cadenaDeDosCorrecciones_seRecorreEntera() {
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        TimeEntry original = TimeEntry.builder().id(5L).usuario(empleado).empresa(empresa).anulado(true).build();
        TimeEntry primera = TimeEntry.builder().id(6L).usuario(empleado).empresa(empresa)
                .registroOriginal(original).anulado(true).build();
        TimeEntry segunda = TimeEntry.builder().id(7L).usuario(empleado).empresa(empresa)
                .registroOriginal(primera).build();

        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(6L)).thenReturn(Optional.of(primera));
        when(timeEntryRepository.findByRegistroOriginal_Id(5L)).thenReturn(Optional.of(primera));
        when(timeEntryRepository.findByRegistroOriginal_Id(6L)).thenReturn(Optional.of(segunda));
        when(timeEntryRepository.findByRegistroOriginal_Id(7L)).thenReturn(Optional.empty());
        when(timeEntryAuditRepository.findByRegistro_IdInOrderByFechaHoraAsc(List.of(5L, 6L, 7L)))
                .thenReturn(List.of());

        service.getAuditTrail(rrhh.getEmail(), 6L);

        verify(timeEntryAuditRepository).findByRegistro_IdInOrderByFechaHoraAsc(List.of(5L, 6L, 7L));
    }

    @Test
    @DisplayName("getAuditTrail de un fichaje de OTRA empresa lanza TenantAccessException")
    void getAuditTrail_fichajeDeOtraEmpresa_lanzaTenantAccessException() {
        Company otraEmpresa = Company.builder().id(2L).build();
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        TimeEntry entry = TimeEntry.builder().id(5L).usuario(empleado).empresa(otraEmpresa).build();
        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.getAuditTrail(rrhh.getEmail(), 5L))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("getAuditTrail de un fichaje inexistente lanza ResourceNotFoundException")
    void getAuditTrail_fichajeInexistente_lanzaResourceNotFoundException() {
        User rrhh = User.builder().id(30L).email("rrhh@nxtime.test").empresa(empresa).build();
        when(userRepository.findByEmail(rrhh.getEmail())).thenReturn(Optional.of(rrhh));
        when(timeEntryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAuditTrail(rrhh.getEmail(), 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
