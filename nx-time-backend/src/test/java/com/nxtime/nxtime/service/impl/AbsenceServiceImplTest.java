package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.UpdateAbsenceStatusRequest;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.mapper.AbsenceMapper;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.VacationBalanceService;
import com.nxtime.nxtime.service.WorkingDayService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios (Mockito) de las reglas de negocio de ausencias: las
 * transiciones de estado, el aislamiento multi-tenant de
 * {@code changeRequestStatus} y, desde la Fase 9, el solapamiento con
 * otras ausencias vivas, el saldo de vacaciones y la trazabilidad de
 * quién resolvió cada petición.
 */
@ExtendWith(MockitoExtension.class)
class AbsenceServiceImplTest {

    @Mock
    private AbsenceRequestRepository absenceRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AbsenceMapper absenceMapper;
    @Mock
    private WorkingDayService workingDayService;
    @Mock
    private VacationBalanceService vacationBalanceService;

    private AbsenceServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User empleado;
    private User gestor;

    private static final AbsenceResponse RESPUESTA_CUALQUIERA =
            new AbsenceResponse(1L, null, null, null, null, AbsenceStatus.PENDIENTE, null, null, null, null, 0);

    @BeforeEach
    void setUp() {
        service = new AbsenceServiceImpl(
                absenceRequestRepository, userRepository, absenceMapper, workingDayService, vacationBalanceService);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra Empresa").build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").empresa(empresa).build();
        gestor = User.builder().id(20L).email("gestor@nxtime.test").empresa(empresa).build();

        // lenient: varios tests cortan con una excepción antes de llegar
        // a mapear la respuesta o a contar días.
        lenient().when(absenceMapper.toResponse(any(AbsenceRequest.class), anyInt()))
                .thenReturn(RESPUESTA_CUALQUIERA);
        lenient().when(workingDayService.contarDiasHabiles(any(), any(), any())).thenReturn(3);
    }

    private AbsenceRequestDTO vacaciones(LocalDate desde, LocalDate hasta) {
        return new AbsenceRequestDTO(desde, hasta, AbsenceType.VACACIONES, "Vacaciones de verano");
    }

    private void conSaldoDisponible(int dias) {
        when(vacationBalanceService.getBalance(eq(empleado), anyInt()))
                .thenReturn(new VacationBalanceResponse(2026, 22, 22 - dias, dias));
    }

    // ---- createRequest ----

    @Test
    @DisplayName("createRequest con fechas válidas y saldo suficiente guarda la petición como PENDIENTE")
    void createRequest_fechasValidasYSaldoSuficiente_guardaPeticion() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(absenceRequestRepository.findSolapadas(any(), any(), any())).thenReturn(List.of());
        conSaldoDisponible(20);
        when(absenceRequestRepository.save(any(AbsenceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRequest(empleado.getEmail(), vacaciones(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5)));

        verify(absenceRequestRepository).save(argThat(req ->
                req.getUsuario().equals(empleado)
                        && req.getEmpresa().equals(empresa)
                        && req.getEstado() == AbsenceStatus.PENDIENTE
                        // Una petición recién creada no está resuelta: sin resolutor ni fecha.
                        && req.getAprobadoPor() == null
                        && req.getFechaResolucion() == null));
    }

    @Test
    @DisplayName("createRequest con fecha de inicio posterior a la de fin lanza BusinessException 400")
    void createRequest_fechaInicioPosteriorAFin_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));

        assertThatThrownBy(() -> service.createRequest(
                        empleado.getEmail(), vacaciones(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 5))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no puede ser posterior");
        verify(absenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRequest que se solapa con otra ausencia viva lanza BusinessException")
    void createRequest_seSolapaConOtraAusenciaViva_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        AbsenceRequest yaExistente = AbsenceRequest.builder().id(1L).usuario(empleado).empresa(empresa)
                .fechaInicio(LocalDate.of(2026, 6, 3)).fechaFin(LocalDate.of(2026, 6, 8))
                .estado(AbsenceStatus.APROBADA).build();
        when(absenceRequestRepository.findSolapadas(any(), any(), any())).thenReturn(List.of(yaExistente));

        assertThatThrownBy(() -> service.createRequest(
                        empleado.getEmail(), vacaciones(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("se solapa");
        verify(absenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRequest de vacaciones sin saldo suficiente lanza BusinessException")
    void createRequest_sinSaldoSuficiente_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(absenceRequestRepository.findSolapadas(any(), any(), any())).thenReturn(List.of());
        when(workingDayService.contarDiasHabiles(any(), any(), any())).thenReturn(10);
        conSaldoDisponible(4); // pide 10 días hábiles y solo le quedan 4

        assertThatThrownBy(() -> service.createRequest(
                        empleado.getEmail(), vacaciones(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Saldo de vacaciones insuficiente");
        verify(absenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("El saldo NO se comprueba en ausencias que no son vacaciones (una baja médica no consume días)")
    void createRequest_bajaMedica_noComprubaSaldoDeVacaciones() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(absenceRequestRepository.findSolapadas(any(), any(), any())).thenReturn(List.of());
        when(absenceRequestRepository.save(any(AbsenceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRequest(empleado.getEmail(), new AbsenceRequestDTO(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), AbsenceType.MEDICO, "Operación"));

        verify(vacationBalanceService, never()).getBalance(any(), anyInt());
        verify(absenceRequestRepository).save(any());
    }

    @Test
    @DisplayName("createRequest de vacaciones que solo abarca fines de semana (0 días hábiles) lanza BusinessException")
    void createRequest_sinDiasHabiles_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        when(absenceRequestRepository.findSolapadas(any(), any(), any())).thenReturn(List.of());
        when(workingDayService.contarDiasHabiles(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.createRequest(
                        empleado.getEmail(), vacaciones(LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 7))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ningún día hábil");
    }

    // ---- Consultas ----

    @Test
    @DisplayName("getMyRequests devuelve las peticiones del usuario mapeadas")
    void getMyRequests_devuelvePeticionesDelUsuario() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        AbsenceRequest request = AbsenceRequest.builder().id(1L).usuario(empleado).empresa(empresa).build();
        when(absenceRequestRepository.findByUsuario(empleado)).thenReturn(List.of(request));

        assertThat(service.getMyRequests(empleado.getEmail())).containsExactly(RESPUESTA_CUALQUIERA);
    }

    @Test
    @DisplayName("getPendingRequests filtra por la empresa del gestor y estado PENDIENTE")
    void getPendingRequests_filtraPorEmpresaYPendiente() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(absenceRequestRepository.findByEmpresa_IdAndEstado(empresa.getId(), AbsenceStatus.PENDIENTE))
                .thenReturn(List.of());

        service.getPendingRequests(gestor.getEmail());

        verify(absenceRequestRepository).findByEmpresa_IdAndEstado(empresa.getId(), AbsenceStatus.PENDIENTE);
    }

    @Test
    @DisplayName("getHistory filtra por la empresa del gestor y estado distinto de PENDIENTE")
    void getHistory_filtraPorEmpresaYNoPendiente() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(absenceRequestRepository.findByEmpresa_IdAndEstadoIsNot(empresa.getId(), AbsenceStatus.PENDIENTE))
                .thenReturn(List.of());

        service.getHistory(gestor.getEmail());

        verify(absenceRequestRepository).findByEmpresa_IdAndEstadoIsNot(empresa.getId(), AbsenceStatus.PENDIENTE);
    }

    // ---- changeRequestStatus ----

    private AbsenceRequest peticionPendiente(Company empresaDeLaPeticion) {
        return AbsenceRequest.builder().id(5L).usuario(empleado).empresa(empresaDeLaPeticion)
                .fechaInicio(LocalDate.of(2026, 6, 1)).fechaFin(LocalDate.of(2026, 6, 5))
                .estado(AbsenceStatus.PENDIENTE).build();
    }

    @Test
    @DisplayName("Aprobar una petición PENDIENTE registra estado, resolutor y fecha de resolución")
    void changeRequestStatus_aprobar_registraTrazabilidad() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        AbsenceRequest request = peticionPendiente(empresa);
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(absenceRequestRepository.save(any(AbsenceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changeRequestStatus(gestor.getEmail(), 5L,
                new UpdateAbsenceStatusRequest(AbsenceStatus.APROBADA, "Adelante."));

        assertThat(request.getEstado()).isEqualTo(AbsenceStatus.APROBADA);
        assertThat(request.getAprobadoPor()).isEqualTo(gestor);
        assertThat(request.getFechaResolucion()).isNotNull();
        assertThat(request.getComentarioResolucion()).isEqualTo("Adelante.");
    }

    @Test
    @DisplayName("Aprobar sin comentario es válido (solo el rechazo lo exige)")
    void changeRequestStatus_aprobarSinComentario_esValido() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        AbsenceRequest request = peticionPendiente(empresa);
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(absenceRequestRepository.save(any(AbsenceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changeRequestStatus(gestor.getEmail(), 5L,
                new UpdateAbsenceStatusRequest(AbsenceStatus.APROBADA, null));

        assertThat(request.getEstado()).isEqualTo(AbsenceStatus.APROBADA);
    }

    @Test
    @DisplayName("Rechazar SIN comentario lanza BusinessException 400: negar una ausencia hay que justificarlo")
    void changeRequestStatus_rechazarSinComentario_lanzaBusinessException() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(peticionPendiente(empresa)));

        assertThatThrownBy(() -> service.changeRequestStatus(gestor.getEmail(), 5L,
                        new UpdateAbsenceStatusRequest(AbsenceStatus.RECHAZADA, "   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("obligatorio indicar un motivo");
        verify(absenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Devolver una petición al estado PENDIENTE lanza BusinessException 400")
    void changeRequestStatus_aPendiente_lanzaBusinessException() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(peticionPendiente(empresa)));

        assertThatThrownBy(() -> service.changeRequestStatus(gestor.getEmail(), 5L,
                        new UpdateAbsenceStatusRequest(AbsenceStatus.PENDIENTE, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Resolver una petición de OTRA empresa lanza TenantAccessException (aislamiento multi-tenant)")
    void changeRequestStatus_peticionDeOtraEmpresa_lanzaTenantAccessException() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(peticionPendiente(otraEmpresa)));

        assertThatThrownBy(() -> service.changeRequestStatus(gestor.getEmail(), 5L,
                        new UpdateAbsenceStatusRequest(AbsenceStatus.APROBADA, null)))
                .isInstanceOf(TenantAccessException.class);
        verify(absenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Resolver una petición ya resuelta lanza BusinessException")
    void changeRequestStatus_peticionYaResuelta_lanzaBusinessException() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        AbsenceRequest request = peticionPendiente(empresa);
        request.setEstado(AbsenceStatus.APROBADA);
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.changeRequestStatus(gestor.getEmail(), 5L,
                        new UpdateAbsenceStatusRequest(AbsenceStatus.RECHAZADA, "No procede.")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDIENTE");
    }

    @Test
    @DisplayName("Resolver una petición inexistente lanza ResourceNotFoundException")
    void changeRequestStatus_peticionInexistente_lanzaResourceNotFoundException() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(absenceRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRequestStatus(gestor.getEmail(), 99L,
                        new UpdateAbsenceStatusRequest(AbsenceStatus.APROBADA, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- Saldo ----

    @Test
    @DisplayName("getMyVacationBalance delega en VacationBalanceService con el usuario y año pedidos")
    void getMyVacationBalance_delegaEnElServicioDeSaldo() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        VacationBalanceResponse saldo = new VacationBalanceResponse(2026, 22, 5, 17);
        when(vacationBalanceService.getBalance(empleado, 2026)).thenReturn(saldo);

        assertThat(service.getMyVacationBalance(empleado.getEmail(), 2026)).isEqualTo(saldo);
    }
}
