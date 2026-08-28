package com.nxtime.nxtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code @DataJpaTest} de AbsenceRequestRepository contra un PostgreSQL
 * real: el aislamiento multi-tenant de estas dos consultas es justo lo
 * que evita que {@code AbsenceServiceImpl.getPendingRequests}/{@code
 * getHistory} mezclen peticiones de otra empresa (ver
 * {@code AbsenceServiceImplTest} para las transiciones de estado en sí).
 */
class AbsenceRequestRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private AbsenceRequestRepository absenceRequestRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private Company empresa;
    private Company otraEmpresa;
    private User empleado;
    private User gestor;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa Test").build());
        otraEmpresa = companyRepository.save(Company.builder().nombre("Otra Empresa").build());
        empleado = userRepository.save(User.builder()
                .email("empleado@nxtime.test").nombre("Empleado").contrasena("hash")
                .rol(Role.EMPLEADO).empresa(empresa).build());
        gestor = userRepository.save(User.builder()
                .email("gestor@nxtime.test").nombre("Gestor").contrasena("hash")
                .rol(Role.GESTOR).empresa(empresa).build());
    }

    /**
     * Desde la Fase 9, una petición resuelta SIEMPRE lleva resolutor y
     * fecha, y lo comprueba la propia base de datos
     * (ck_peticiones_resolucion_coherente): construir una APROBADA sin
     * ellos aquí haría fallar el INSERT, no el assert -- que es
     * exactamente lo que se quiere de esa restricción.
     */
    private AbsenceRequest peticion(Company company, AbsenceStatus estado) {
        AbsenceRequest.AbsenceRequestBuilder builder = AbsenceRequest.builder()
                .usuario(empleado).empresa(company)
                .fechaInicio(LocalDate.of(2026, 6, 1)).fechaFin(LocalDate.of(2026, 6, 5))
                .tipo(AbsenceType.VACACIONES).estado(estado);

        if (estado != AbsenceStatus.PENDIENTE) {
            builder.aprobadoPor(gestor).fechaResolucion(Instant.now());
        }
        return absenceRequestRepository.save(builder.build());
    }

    @Test
    @DisplayName("findByEmpresa_IdAndEstado solo devuelve peticiones PENDIENTE de la empresa indicada")
    void findByEmpresaIdAndEstado_filtraPorEmpresaYEstado() {
        AbsenceRequest pendienteDeLaEmpresa = peticion(empresa, AbsenceStatus.PENDIENTE);
        peticion(empresa, AbsenceStatus.APROBADA);
        peticion(otraEmpresa, AbsenceStatus.PENDIENTE);

        List<AbsenceRequest> result =
                absenceRequestRepository.findByEmpresa_IdAndEstado(empresa.getId(), AbsenceStatus.PENDIENTE);

        assertThat(result).extracting(AbsenceRequest::getId).containsExactly(pendienteDeLaEmpresa.getId());
    }

    @Test
    @DisplayName("findByEmpresa_IdAndEstadoIsNot excluye las PENDIENTE y las de otra empresa")
    void findByEmpresaIdAndEstadoIsNot_excluyePendientesYOtraEmpresa() {
        peticion(empresa, AbsenceStatus.PENDIENTE);
        AbsenceRequest aprobada = peticion(empresa, AbsenceStatus.APROBADA);
        AbsenceRequest rechazada = peticion(empresa, AbsenceStatus.RECHAZADA);
        peticion(otraEmpresa, AbsenceStatus.APROBADA);

        List<AbsenceRequest> result = absenceRequestRepository
                .findByEmpresa_IdAndEstadoIsNot(empresa.getId(), AbsenceStatus.PENDIENTE);

        assertThat(result).extracting(AbsenceRequest::getId)
                .containsExactlyInAnyOrder(aprobada.getId(), rechazada.getId());
    }
}
