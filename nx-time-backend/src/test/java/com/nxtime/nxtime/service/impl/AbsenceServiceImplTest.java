package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.mapper.AbsenceMapper;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.UserRepository;
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
 * Unitarios (Mockito) de las transiciones de estado de una petición de
 * ausencia y de la validación de orden de fechas, incluido el
 * aislamiento multi-tenant de {@code changeRequestStatus} (ver
 * auditoría y {@link com.nxtime.nxtime.exception.TenantAccessException}).
 */
@ExtendWith(MockitoExtension.class)
class AbsenceServiceImplTest {

    @Mock
    private AbsenceRequestRepository absenceRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AbsenceMapper absenceMapper;

    private AbsenceServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User empleado;
    private User gestor;

    @BeforeEach
    void setUp() {
        service = new AbsenceServiceImpl(absenceRequestRepository, userRepository, absenceMapper);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra Empresa").build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").empresa(empresa).build();
        gestor = User.builder().id(20L).email("gestor@nxtime.test").empresa(empresa).build();
    }

    @Test
    @DisplayName("createRequest con fechas válidas guarda la petición como PENDIENTE")
    void createRequest_fechasValidas_guardaPeticion() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        AbsenceRequestDTO dto = new AbsenceRequestDTO(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), AbsenceType.VACACIONES, "Vacaciones de verano");
        when(absenceRequestRepository.save(any(AbsenceRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        AbsenceResponse response = new AbsenceResponse(
                1L, dto.fechaInicio(), dto.fechaFin(), dto.tipo(), dto.motivo(), AbsenceStatus.PENDIENTE, null);
        when(absenceMapper.toResponse(any(AbsenceRequest.class))).thenReturn(response);

        AbsenceResponse result = service.createRequest(empleado.getEmail(), dto);

        assertThat(result).isEqualTo(response);
        verify(absenceRequestRepository).save(argThat(req ->
                req.getUsuario().equals(empleado)
                        && req.getEmpresa().equals(empresa)
                        && req.getEstado() == AbsenceStatus.PENDIENTE));
    }

    @Test
    @DisplayName("createRequest con fecha de inicio posterior a la de fin lanza BusinessException 400")
    void createRequest_fechaInicioPosteriorAFin_lanzaBusinessException() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        AbsenceRequestDTO dto = new AbsenceRequestDTO(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 5), AbsenceType.VACACIONES, null);

        assertThatThrownBy(() -> service.createRequest(empleado.getEmail(), dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no puede ser posterior");
        verify(absenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("getMyRequests devuelve las peticiones del usuario mapeadas")
    void getMyRequests_devuelvePeticionesDelUsuario() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
        AbsenceRequest request = AbsenceRequest.builder().id(1L).usuario(empleado).empresa(empresa).build();
        when(absenceRequestRepository.findByUsuario(empleado)).thenReturn(List.of(request));
        AbsenceResponse response = new AbsenceResponse(1L, null, null, null, null, AbsenceStatus.PENDIENTE, null);
        when(absenceMapper.toResponse(request)).thenReturn(response);

        assertThat(service.getMyRequests(empleado.getEmail())).containsExactly(response);
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
    @DisplayName("changeRequestStatus aprueba una petición PENDIENTE de la misma empresa")
    void changeRequestStatus_peticionPendienteMismaEmpresa_laAprueba() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        AbsenceRequest request = AbsenceRequest.builder().id(5L).usuario(empleado).empresa(empresa)
                .estado(AbsenceStatus.PENDIENTE).build();
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(absenceRequestRepository.save(any(AbsenceRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        AbsenceResponse response = new AbsenceResponse(5L, null, null, null, null, AbsenceStatus.APROBADA, null);
        when(absenceMapper.toResponse(any(AbsenceRequest.class))).thenReturn(response);

        AbsenceResponse result = service.changeRequestStatus(gestor.getEmail(), 5L, AbsenceStatus.APROBADA);

        assertThat(result).isEqualTo(response);
        assertThat(request.getEstado()).isEqualTo(AbsenceStatus.APROBADA);
    }

    @Test
    @DisplayName("changeRequestStatus de una petición de OTRA empresa lanza TenantAccessException "
            + "(aislamiento multi-tenant)")
    void changeRequestStatus_peticionDeOtraEmpresa_lanzaTenantAccessException() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        AbsenceRequest request = AbsenceRequest.builder().id(5L).usuario(empleado).empresa(otraEmpresa)
                .estado(AbsenceStatus.PENDIENTE).build();
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.changeRequestStatus(gestor.getEmail(), 5L, AbsenceStatus.APROBADA))
                .isInstanceOf(TenantAccessException.class);
        verify(absenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeRequestStatus sobre una petición ya resuelta lanza BusinessException")
    void changeRequestStatus_peticionYaResuelta_lanzaBusinessException() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        AbsenceRequest request = AbsenceRequest.builder().id(5L).usuario(empleado).empresa(empresa)
                .estado(AbsenceStatus.APROBADA).build();
        when(absenceRequestRepository.findById(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.changeRequestStatus(gestor.getEmail(), 5L, AbsenceStatus.RECHAZADA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDIENTE");
    }

    @Test
    @DisplayName("changeRequestStatus sobre una petición inexistente lanza ResourceNotFoundException")
    void changeRequestStatus_peticionInexistente_lanzaResourceNotFoundException() {
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(absenceRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRequestStatus(gestor.getEmail(), 99L, AbsenceStatus.APROBADA))
                .isInstanceOf(ResourceNotFoundException.class);
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
}
