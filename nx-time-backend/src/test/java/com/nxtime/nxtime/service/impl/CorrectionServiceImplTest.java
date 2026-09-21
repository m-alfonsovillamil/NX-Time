package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.DisputeRequest;
import com.nxtime.nxtime.dto.ResolveCorrectionRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AddedPauseRepository;
import com.nxtime.nxtime.repository.CorrectionRequestRepository;
import com.nxtime.nxtime.repository.ProposedAllocationRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.ValidadorDeReparto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Correcciones con aprobación (Fase E).
 *
 * Casi todos los casos giran alrededor de <b>quién puede resolver
 * qué</b>, que es la regla que da sentido a la fase y la que, si se
 * rompe, deja a alguien cambiando las horas de otro sin permiso —
 * exactamente lo que esto vino a impedir.
 */
@ExtendWith(MockitoExtension.class)
class CorrectionServiceImplTest {

    @Mock
    private CorrectionRequestRepository correctionRepository;
    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TimeEntrySnapshotSerializer snapshotSerializer;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AddedPauseRepository addedPauseRepository;
    @Mock
    private ProposedAllocationRepository proposedAllocationRepository;
    @Mock
    private ValidadorDeReparto validadorDeReparto;

    private CorrectionServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User empleado;
    private User otroEmpleado;
    private User gestor;
    private User rrhh;
    private TimeEntry fichajeDelEmpleado;

    private static final Instant ENTRADA = Instant.parse("2026-06-01T07:00:00Z");
    private static final Instant SALIDA = Instant.parse("2026-06-01T15:00:00Z");

    @BeforeEach
    void setUp() {
        service = new CorrectionServiceImpl(
                correctionRepository, timeEntryRepository, userRepository,
                snapshotSerializer, eventPublisher, addedPauseRepository,
                org.mockito.Mockito.mock(com.nxtime.nxtime.service.ProjectAllocationService.class),
                proposedAllocationRepository, validadorDeReparto);

        empresa = Company.builder().id(1L).nombre("TechCorp").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra").build();
        empleado = User.builder().id(10L).email("ana@test.com").nombre("Ana")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build();
        otroEmpleado = User.builder().id(11L).email("javi@test.com").nombre("Javi")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build();
        gestor = User.builder().id(20L).email("marta@test.com").nombre("Marta")
                .rol(Role.GESTOR).empresa(empresa).activo(true).build();
        rrhh = User.builder().id(30L).email("elena@test.com").nombre("Elena")
                .rol(Role.RRHH).empresa(empresa).activo(true).build();

        fichajeDelEmpleado = TimeEntry.builder()
                .id(5L).usuario(empleado).empresa(empresa)
                .horaEntrada(ENTRADA).horaSalida(SALIDA).anulado(false).build();

        lenient().when(snapshotSerializer.toJson(any())).thenReturn("{}");
    }

    private CorrectionRequestDTO peticion() {
        return new CorrectionRequestDTO(ENTRADA, SALIDA.plusSeconds(3600), "Olvidé fichar la salida");
    }

    /** La misma petición, pero llevando dentro un reparto por proyecto (ADR 017). */
    private CorrectionRequestDTO peticionConReparto(long proyectoId, long minutos) {
        return new CorrectionRequestDTO(ENTRADA, SALIDA.plusSeconds(3600), "Olvidé fichar la salida",
                null, null, List.of(new CorrectionRequestDTO.ProjectShare(proyectoId, minutos)));
    }

    private CorrectionRequest solicitudDe(User solicitante, CorrectionStatus estado) {
        return CorrectionRequest.builder()
                .id(77L).empresa(empresa).registro(fichajeDelEmpleado).solicitante(solicitante)
                .horaEntradaPropuesta(ENTRADA).horaSalidaPropuesta(SALIDA.plusSeconds(3600))
                .motivo("Olvidé fichar la salida").estado(estado)
                .creadoEn(Instant.now()).build();
    }

    private void alGuardarDevolverLoMismo() {
        when(correctionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------
    // Pedir
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Un empleado pide corregir SU fichaje: queda pendiente y el fichaje NO se toca")
    void solicitar_empleadoSobreSuFichaje_quedaPendiente() {
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(fichajeDelEmpleado));
        when(correctionRepository.findVivaDelRegistro(5L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();
        when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(empleado, gestor));

        CorrectionResponse respuesta = service.solicitar(5L, peticion(), empleado);

        assertThat(respuesta.estado()).isEqualTo(CorrectionStatus.PENDIENTE);
        // Lo esencial de la fase: el registro se queda como estaba.
        assertThat(fichajeDelEmpleado.isAnulado()).isFalse();
        verify(timeEntryRepository, never()).save(any());
    }

    /**
     * La comprobación que faltaba (Fase A3).
     *
     * El endpoint de correcciones acepta un reparto por proyecto, y hasta
     * septiembre de 2026 aquí solo se miraba que el proyecto fuera de la misma
     * empresa: bastaba un cliente HTTP para imputar horas a un proyecto en el
     * que nunca se estuvo, y al aprobarse se escribía sin volver a mirar.
     * Ahora pasa por el mismo validador que el reparto libre.
     */
    @Test
    @DisplayName("Una corrección con reparto lo valida antes de guardarlo")
    void solicitar_conReparto_pasaPorElValidador() {
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(fichajeDelEmpleado));
        when(correctionRepository.findVivaDelRegistro(5L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();
        when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(empleado, gestor));
        when(validadorDeReparto.validarYResolver(any(), any())).thenReturn(Map.of());

        service.solicitar(5L, peticionConReparto(7L, 120), empleado);

        verify(validadorDeReparto).validarYResolver(
                eq(fichajeDelEmpleado), eq(List.of(new ValidadorDeReparto.Linea(7L, 120))));
    }

    @Test
    @DisplayName("Si el reparto no vale, la corrección no se guarda con él")
    void solicitar_conRepartoInvalido_propagaElError() {
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(fichajeDelEmpleado));
        when(correctionRepository.findVivaDelRegistro(5L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();
        when(validadorDeReparto.validarYResolver(any(), any())).thenThrow(new BusinessException(
                "Ese día no estabas asignado a alguno de los proyectos del reparto.", HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.solicitar(5L, peticionConReparto(99L, 120), empleado))
                .isInstanceOf(BusinessException.class);

        // El reparto no llega a la base. La solicitud sí se habrá guardado
        // antes, pero la transacción del servicio hace rollback: por eso este
        // test mira el reparto y no la solicitud.
        verify(proposedAllocationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Una corrección sin reparto no molesta al validador")
    void solicitar_sinReparto_niSiquieraValida() {
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(fichajeDelEmpleado));
        when(correctionRepository.findVivaDelRegistro(5L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();
        when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(empleado, gestor));

        // El caso normal: corregir solo las horas.
        service.solicitar(5L, peticion(), empleado);

        verify(validadorDeReparto, never()).validarYResolver(any(), any());
        verify(proposedAllocationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Un empleado NO puede pedir correcciones del fichaje de otro")
    void solicitar_sobreFichajeAjenoSinAuthority_da403() {
        TimeEntry ajeno = TimeEntry.builder().id(6L).usuario(otroEmpleado).empresa(empresa)
                .horaEntrada(ENTRADA).horaSalida(SALIDA).build();
        when(timeEntryRepository.findById(6L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.solicitar(6L, peticion(), empleado))
                .isInstanceOf(TenantAccessException.class);
    }

    /**
     * El caso que hace falta para que un ADMIN no quede bloqueado: si
     * tuviera que esperar a que alguien se lo apruebe, no habría nadie.
     */
    @Test
    @DisplayName("Quien pide sobre SU fichaje, puede aprobar y NO hay nadie más que pueda, se auto-aprueba")
    void solicitar_dueñoQuePuedeAprobar_seAplicaEnElActo() {
        TimeEntry suyo = TimeEntry.builder().id(7L).usuario(gestor).empresa(empresa)
                .horaEntrada(ENTRADA).horaSalida(SALIDA).anulado(false).build();
        when(timeEntryRepository.findById(7L)).thenReturn(Optional.of(suyo));
        // El gestor es el único con permiso de aprobar: el empleado no lo tiene.
        when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(gestor, empleado));
        when(correctionRepository.findVivaDelRegistro(7L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(timeEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CorrectionResponse respuesta = service.solicitar(7L, peticion(), gestor);

        assertThat(respuesta.estado()).isEqualTo(CorrectionStatus.APROBADA);
        assertThat(suyo.isAnulado()).isTrue();
    }

    /*
     * 17/09/2026, visto en el piloto: un gestor se aprobaba sus propias
     * correcciones aunque hubiera otras personas que podían revisarlas. Es
     * un conflicto de interés, y las horas extra ya lo prohíben.
     */
    @Test
    @DisplayName("Un gestor NO se auto-aprueba si hay otra persona que puede aprobar: le llega a ella")
    void solicitar_dueñoQuePuedeAprobar_conOtroAprobador_quedaPendiente() {
        TimeEntry suyo = TimeEntry.builder().id(7L).usuario(gestor).empresa(empresa)
                .horaEntrada(ENTRADA).horaSalida(SALIDA).anulado(false).build();
        when(timeEntryRepository.findById(7L)).thenReturn(Optional.of(suyo));
        when(correctionRepository.findVivaDelRegistro(7L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();
        when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(gestor, rrhh, empleado));

        CorrectionResponse respuesta = service.solicitar(7L, peticion(), gestor);

        assertThat(respuesta.estado()).isEqualTo(CorrectionStatus.PENDIENTE);
        assertThat(suyo.isAnulado()).isFalse();
        verify(timeEntryRepository, never()).save(any());

        ArgumentCaptor<Object> eventos = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(eventos.capture());
        assertThat(eventos.getAllValues())
                .filteredOn(com.nxtime.nxtime.notification.NotificationEvents.CorrectionRequested.class::isInstance)
                .singleElement()
                .satisfies(e -> assertThat(
                        ((com.nxtime.nxtime.notification.NotificationEvents.CorrectionRequested) e).destinatarios())
                        .containsExactly(rrhh));
    }

    @Test
    @DisplayName("La auto-aprobación queda escrita en la traza; es lo que la hace aceptable")
    void solicitar_autoAprobacion_dejaTraza() {
        TimeEntry suyo = TimeEntry.builder().id(7L).usuario(gestor).empresa(empresa)
                .horaEntrada(ENTRADA).horaSalida(SALIDA).anulado(false).build();
        when(timeEntryRepository.findById(7L)).thenReturn(Optional.of(suyo));
        when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(gestor, empleado));
        when(correctionRepository.findVivaDelRegistro(7L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(timeEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.solicitar(7L, peticion(), gestor);

        ArgumentCaptor<Object> eventos = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(eventos.capture());
        List<AuditAction> acciones = eventos.getAllValues().stream()
                .filter(TimeEntryAuditEvent.class::isInstance)
                .map(e -> ((TimeEntryAuditEvent) e).auditRow().getAccion())
                .toList();
        assertThat(acciones).contains(AuditAction.SOLICITUD_CORRECCION, AuditAction.CORRECCION);
    }

    @Test
    @DisplayName("Un fichaje con una solicitud viva no admite otra")
    void solicitar_conSolicitudViva_da409() {
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(fichajeDelEmpleado));
        when(correctionRepository.findVivaDelRegistro(5L))
                .thenReturn(Optional.of(solicitudDe(empleado, CorrectionStatus.PENDIENTE)));

        assertThatThrownBy(() -> service.solicitar(5L, peticion(), empleado))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sin resolver");
    }

    @Test
    @DisplayName("Una jornada sin cerrar no se puede corregir")
    void solicitar_jornadaAbierta_da409() {
        TimeEntry abierta = TimeEntry.builder().id(8L).usuario(empleado).empresa(empresa)
                .horaEntrada(ENTRADA).horaSalida(null).build();
        when(timeEntryRepository.findById(8L)).thenReturn(Optional.of(abierta));

        assertThatThrownBy(() -> service.solicitar(8L, peticion(), empleado))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un fichaje de otra empresa no se toca (ADR 006)")
    void solicitar_deOtraEmpresa_da403() {
        TimeEntry ajeno = TimeEntry.builder().id(9L).usuario(empleado).empresa(otraEmpresa)
                .horaEntrada(ENTRADA).horaSalida(SALIDA).build();
        when(timeEntryRepository.findById(9L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.solicitar(9L, peticion(), empleado))
                .isInstanceOf(TenantAccessException.class);
    }

    // ---------------------------------------------------------------
    // Quién resuelve: el corazón de la fase
    // ---------------------------------------------------------------

    @Test
    @DisplayName("La pide el empleado: la aprueba el gestor, y aplicarla anula el original")
    void resolver_gestorApruebaLaDelEmpleado_seAplica() {
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(timeEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CorrectionResponse respuesta =
                service.resolver(77L, new ResolveCorrectionRequest(true, null), gestor);

        assertThat(respuesta.estado()).isEqualTo(CorrectionStatus.APROBADA);
        assertThat(fichajeDelEmpleado.isAnulado()).isTrue();
    }

    /*
     * -----------------------------------------------------------------
     * Las pausas al corregir (16/09/2026).
     *
     * El fichaje corregido se construía con un builder en línea que NO
     * copiaba `segundosPausaAcumulados`, y el campo tiene
     * @Builder.Default = 0. Resultado: aprobar CUALQUIER corrección
     * borraba las pausas de esa jornada e inflaba el tiempo neto. Como el
     * neto alimenta al detector de horas extra, una corrección aprobada
     * podía fabricar horas extra que nadie hizo.
     * -----------------------------------------------------------------
     */

    @Test
    @DisplayName("Aprobar una corrección NO borra las pausas: el fichaje corregido las hereda")
    void resolver_alAplicar_conservaLasPausasDelOriginal() {
        fichajeDelEmpleado.setSegundosPausaAcumulados(2700); // 45 min de comida
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(timeEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resolver(77L, new ResolveCorrectionRequest(true, null), gestor);

        ArgumentCaptor<TimeEntry> guardados = ArgumentCaptor.forClass(TimeEntry.class);
        verify(timeEntryRepository, org.mockito.Mockito.atLeastOnce()).save(guardados.capture());
        TimeEntry corregido = guardados.getAllValues().stream()
                .filter(entry -> entry.getRegistroOriginal() != null)
                .findFirst()
                .orElseThrow();
        assertThat(corregido.getSegundosPausaAcumulados()).isEqualTo(2700);
    }

    /**
     * La otra mitad, y va con test propio para que nadie la "arregle": la
     * marca de jornada cerrada por el proceso nocturno <b>no</b> se
     * hereda. Corregir la jornada es justo lo que resuelve esa
     * incidencia, y {@code contarIncidenciasAbiertas} dejaría de cuadrar
     * si la copia naciera marcada.
     */
    @Test
    @DisplayName("El fichaje corregido NO hereda la marca de jornada incompleta: corregirla la resuelve")
    void resolver_alAplicar_noHeredaLaMarcaDeJornadaIncompleta() {
        fichajeDelEmpleado.setJornadaIncompleta(true);
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(timeEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resolver(77L, new ResolveCorrectionRequest(true, null), gestor);

        ArgumentCaptor<TimeEntry> guardados = ArgumentCaptor.forClass(TimeEntry.class);
        verify(timeEntryRepository, org.mockito.Mockito.atLeastOnce()).save(guardados.capture());
        TimeEntry corregido = guardados.getAllValues().stream()
                .filter(entry -> entry.getRegistroOriginal() != null)
                .findFirst()
                .orElseThrow();
        assertThat(corregido.isJornadaIncompleta()).isFalse();
    }

    /**
     * Conservar las pausas obliga a una comprobación que antes no hacía
     * falta: unas horas corregidas más cortas que las pausas darían un
     * tiempo neto <b>negativo</b>, y los agregados del repositorio restan
     * en SQL sin proteger el resultado.
     */
    @Test
    @DisplayName("Pedir una corrección que deja las pausas sin caber se rechaza al pedirla")
    void solicitar_horasQueNoDejanSitioALasPausas_falla() {
        // Jornada de 8 h con 2 h de pausa; se pide recortarla a 1 h.
        fichajeDelEmpleado.setSegundosPausaAcumulados(7200);
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(fichajeDelEmpleado));

        CorrectionRequestDTO recorte =
                new CorrectionRequestDTO(ENTRADA, ENTRADA.plusSeconds(3600), "Me equivoqué de hora");

        assertThatThrownBy(() -> service.solicitar(5L, recorte, empleado))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pausas");
        verify(correctionRepository, never()).saveAndFlush(any());
    }

    /**
     * El caso inverso, y el más importante: si la corrección la pide un
     * gestor sobre el fichaje de otro, <b>no puede aprobársela él</b>.
     * Si pudiera, la fase entera no serviría de nada.
     */
    @Test
    @DisplayName("La pide el gestor sobre el fichaje de otro: NO puede aprobársela él mismo")
    void resolver_gestorApruebaLaSuyaSobreOtro_da403() {
        CorrectionRequest solicitud = solicitudDe(gestor, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() ->
                service.resolver(77L, new ResolveCorrectionRequest(true, null), gestor))
                .isInstanceOf(TenantAccessException.class);

        assertThat(fichajeDelEmpleado.isAnulado()).isFalse();
    }

    @Test
    @DisplayName("La pide el gestor sobre el fichaje de otro: la aprueba el DUEÑO, sin ser gestor")
    void resolver_dueñoApruebaLaQuePidioOtro_seAplica() {
        CorrectionRequest solicitud = solicitudDe(gestor, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(timeEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Ana es EMPLEADO y no tiene ninguna authority de aprobación: le
        // basta con que sean SUS horas.
        CorrectionResponse respuesta =
                service.resolver(77L, new ResolveCorrectionRequest(true, null), empleado);

        assertThat(respuesta.estado()).isEqualTo(CorrectionStatus.APROBADA);
    }

    @Test
    @DisplayName("Un tercero sin relación con la solicitud no puede resolverla")
    void resolver_terceroSinRelacion_da403() {
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() ->
                service.resolver(77L, new ResolveCorrectionRequest(true, null), otroEmpleado))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("Rechazar sin comentario es un 400: decir que no sin decir por qué no sirve")
    void resolver_rechazoSinComentario_da400() {
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() ->
                service.resolver(77L, new ResolveCorrectionRequest(false, "  "), gestor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Un rechazo deja traza aunque el fichaje no cambie")
    void resolver_rechazo_dejaTrazaYNoTocaElFichaje() {
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resolver(77L, new ResolveCorrectionRequest(false, "Las horas no cuadran"), gestor);

        assertThat(solicitud.getEstado()).isEqualTo(CorrectionStatus.RECHAZADA);
        assertThat(fichajeDelEmpleado.isAnulado()).isFalse();

        ArgumentCaptor<Object> eventos = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(eventos.capture());
        // Sin esto, un intento de corrección rechazado no dejaría rastro
        // y la traza solo contaría los cambios que salieron adelante.
        assertThat(eventos.getAllValues().stream()
                .filter(TimeEntryAuditEvent.class::isInstance)
                .map(e -> ((TimeEntryAuditEvent) e).auditRow().getAccion()))
                .contains(AuditAction.RECHAZO_CORRECCION);
    }

    @Test
    @DisplayName("Una solicitud ya resuelta no se resuelve dos veces")
    void resolver_yaResuelta_da409() {
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.APROBADA);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() ->
                service.resolver(77L, new ResolveCorrectionRequest(true, null), gestor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya está resuelta");
    }

    /**
     * Entre que se pide y se aprueba puede pasar tiempo. Si el fichaje ya
     * se corrigió por otra vía, aplicar esta crearía una segunda versión
     * "buena" del mismo día.
     */
    @Test
    @DisplayName("Si el fichaje ya se corrigió mientras esperaba, la aprobación falla en vez de duplicarlo")
    void resolver_fichajeYaAnulado_da409() {
        fichajeDelEmpleado.setAnulado(true);
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() ->
                service.resolver(77L, new ResolveCorrectionRequest(true, null), gestor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("otra vía");
    }

    // ---------------------------------------------------------------
    // Disputa
    // ---------------------------------------------------------------

    @Test
    @DisplayName("El dueño no acepta la corrección que le proponen: pasa a EN_DISPUTA, no a rechazada")
    void disputar_dueño_pasaAEnDisputa() {
        CorrectionRequest solicitud = solicitudDe(gestor, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(empleado, gestor, rrhh));

        CorrectionResponse respuesta =
                service.disputar(77L, new DisputeRequest("Ese día salí a las 15:00"), empleado);

        // Rechazarla sería que una de las dos partes se dé la razón: se
        // escala en vez de cerrarse.
        assertThat(respuesta.estado()).isEqualTo(CorrectionStatus.EN_DISPUTA);
        assertThat(solicitud.getMotivoDisputa()).isEqualTo("Ese día salí a las 15:00");
    }

    @Test
    @DisplayName("Solo el dueño del fichaje puede disputar")
    void disputar_noEsElDueño_da403() {
        CorrectionRequest solicitud = solicitudDe(gestor, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() ->
                service.disputar(77L, new DisputeRequest("No me parece"), otroEmpleado))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("No se disputa una corrección que pediste tú")
    void disputar_laPidioElMismo_da400() {
        CorrectionRequest solicitud = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() ->
                service.disputar(77L, new DisputeRequest("Me arrepiento"), empleado))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Una disputa la resuelve RRHH, no el gestor implicado")
    void resolver_enDisputa_soloQuienResuelveDisputas() {
        CorrectionRequest solicitud = solicitudDe(gestor, CorrectionStatus.EN_DISPUTA);
        solicitud.setMotivoDisputa("No estoy de acuerdo");
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));

        // El gestor es parte del conflicto: no decide.
        assertThatThrownBy(() ->
                service.resolver(77L, new ResolveCorrectionRequest(true, null), gestor))
                .isInstanceOf(TenantAccessException.class);

        // Ni el empleado, que es la otra parte.
        assertThatThrownBy(() ->
                service.resolver(77L, new ResolveCorrectionRequest(false, "No"), empleado))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("RRHH sí resuelve la disputa en firme")
    void resolver_enDisputa_rrhhDecide() {
        CorrectionRequest solicitud = solicitudDe(gestor, CorrectionStatus.EN_DISPUTA);
        solicitud.setMotivoDisputa("No estoy de acuerdo");
        when(correctionRepository.findById(77L)).thenReturn(Optional.of(solicitud));
        when(correctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(timeEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CorrectionResponse respuesta =
                service.resolver(77L, new ResolveCorrectionRequest(true, "Revisado con ambos"), rrhh);

        assertThat(respuesta.estado()).isEqualTo(CorrectionStatus.APROBADA);
    }

    // ---------------------------------------------------------------
    // Listados
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Cada cual ve en pendientes solo lo que le toca resolver")
    void pendientesParaMi_filtraPorLaMismaRegla() {
        CorrectionRequest laPidioElEmpleado = solicitudDe(empleado, CorrectionStatus.PENDIENTE);
        CorrectionRequest laPidioElGestor = solicitudDe(gestor, CorrectionStatus.PENDIENTE);
        laPidioElGestor.setId(78L);
        when(correctionRepository.findVivasDeEmpresa(1L))
                .thenReturn(List.of(laPidioElEmpleado, laPidioElGestor));

        // Al gestor le toca la que pidió el empleado, no la suya.
        assertThat(service.pendientesParaMi(gestor)).extracting(CorrectionResponse::id)
                .containsExactly(77L);
        // Al empleado, la que le han propuesto sobre su fichaje.
        assertThat(service.pendientesParaMi(empleado)).extracting(CorrectionResponse::id)
                .containsExactly(78L);
        // Y a alguien ajeno, ninguna.
        assertThat(service.pendientesParaMi(otroEmpleado)).isEmpty();
    }

    @Test
    @DisplayName("La respuesta dice a quien pregunta si puede resolver y si puede disputar")
    void toResponse_calculaLosPermisosParaQuienPregunta() {
        CorrectionRequest laPidioElGestor = solicitudDe(gestor, CorrectionStatus.PENDIENTE);
        when(correctionRepository.findMias(anyLong())).thenReturn(List.of(laPidioElGestor));

        CorrectionResponse paraElDueño = service.mias(empleado).get(0);
        assertThat(paraElDueño.puedoResolver()).isTrue();
        assertThat(paraElDueño.puedoDisputar()).isTrue();

        CorrectionResponse paraUnTercero = service.mias(otroEmpleado).get(0);
        assertThat(paraUnTercero.puedoResolver()).isFalse();
        assertThat(paraUnTercero.puedoDisputar()).isFalse();
    }
}
