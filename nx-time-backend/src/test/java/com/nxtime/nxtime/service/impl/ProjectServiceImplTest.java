package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.ProjectAssignment;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ProjectAssignmentRequest;
import com.nxtime.nxtime.dto.ProjectRequest;
import com.nxtime.nxtime.dto.ProjectResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.ProjectAssignmentRepository;
import com.nxtime.nxtime.repository.ProjectRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Unitarios de proyectos (Fase D).
 *
 * Lo que se prueba no es el CRUD, que es igual que el de departamentos,
 * sino las reglas que si se rompen dejan números mintiendo sin que salte
 * nada: que una asignación no se pueda solapar con otra, que borrar un
 * proyecto con historial no se permita, y que el rechazo del EXCLUDE de
 * la base llegue al cliente como un 409 con explicación y no como un 500.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;

    private ProjectServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User gestor;
    private User empleado;
    private Project proyecto;

    @BeforeEach
    void setUp() {
        service = new ProjectServiceImpl(projectRepository, assignmentRepository, userRepository);

        empresa = Company.builder().id(1L).nombre("TechCorp").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra").build();
        gestor = User.builder().id(10L).email("gestor@test.com")
                .nombre("Marta").rol(Role.GESTOR).empresa(empresa).build();
        empleado = User.builder().id(11L).email("empleado@test.com")
                .nombre("Ana").rol(Role.EMPLEADO).empresa(empresa).build();
        proyecto = Project.builder().id(100L).empresa(empresa).codigo("NX-CORE")
                .nombre("Plataforma").fechaInicio(LocalDate.of(2026, 1, 1)).activo(true).build();
    }

    private ProjectRequest peticion(String codigo, LocalDate inicio, LocalDate fin) {
        return new ProjectRequest(codigo, "Un proyecto", null, inicio, fin);
    }

    // ---------------------------------------------------------------
    // Proyectos
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Crear limpia los espacios del código y nace activo")
    void crear_normalizaYNaceActivo() {
        when(projectRepository.existsByEmpresaAndCodigoIgnoreCase(any(), any())).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse respuesta = service.crear(
                peticion("  NX-CORE  ", LocalDate.of(2026, 1, 1), null), gestor);

        assertThat(respuesta.codigo()).isEqualTo("NX-CORE");
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    @DisplayName("Un código repetido en la misma empresa es un 409")
    void crear_codigoRepetido_da409() {
        when(projectRepository.existsByEmpresaAndCodigoIgnoreCase(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.crear(peticion("NX-CORE", LocalDate.of(2026, 1, 1), null), gestor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Una fecha de fin anterior a la de inicio es un 400")
    void crear_fechasAlReves_da400() {
        assertThatThrownBy(() -> service.crear(
                peticion("NX-CORE", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)), gestor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cambiar solo el nombre no choca con el código de uno mismo")
    void editar_mismoCodigo_noEsConflicto() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(proyecto));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse respuesta = service.editar(100L,
                new ProjectRequest("nx-core", "Otro nombre", null, LocalDate.of(2026, 1, 1), null),
                gestor);

        // Solo cambian las mayúsculas: sin el "no es él mismo" daría 409.
        assertThat(respuesta.nombre()).isEqualTo("Otro nombre");
        verify(projectRepository, never()).existsByEmpresaAndCodigoIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("Un proyecto de otra empresa no se toca (ADR 006)")
    void editar_deOtraEmpresa_da403() {
        Project ajeno = Project.builder().id(200L).empresa(otraEmpresa).codigo("X")
                .fechaInicio(LocalDate.of(2026, 1, 1)).build();
        when(projectRepository.findById(200L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.editar(200L,
                peticion("X", LocalDate.of(2026, 1, 1), null), gestor))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("Un proyecto con asignaciones no se borra: se cierra")
    void borrar_conAsignaciones_da409YSugiereCerrar() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(proyecto));
        when(assignmentRepository.countByProyecto_Id(100L)).thenReturn(3L);

        assertThatThrownBy(() -> service.borrar(100L, gestor))
                .isInstanceOf(BusinessException.class)
                // El mensaje dice CUÁNTAS hay, que es lo que la violación
                // de clave ajena no podría decir, y sugiere cerrarlo.
                .hasMessageContaining("3")
                .hasMessageContaining("ciérralo");

        verify(projectRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Cerrar un proyecto no lo borra ni toca sus asignaciones")
    void cambiarEstado_cierraSinBorrarNada() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(proyecto));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse respuesta = service.cambiarEstado(100L, false, gestor);

        assertThat(respuesta.activo()).isFalse();
        verify(projectRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------
    // Asignaciones: la parte con reglas de verdad
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Asignar a alguien que ya está en otro proyecto dice en cuál")
    void asignar_yaAsignado_da409ConElProyecto() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(proyecto));
        when(userRepository.findById(11L)).thenReturn(Optional.of(empleado));
        Project otro = Project.builder().id(300L).empresa(empresa).codigo("NX-APP").build();
        when(assignmentRepository.findVigenteDe(eq(11L), any())).thenReturn(
                Optional.of(ProjectAssignment.builder().id(1L).usuario(empleado).proyecto(otro)
                        .fechaInicio(LocalDate.of(2026, 1, 1)).build()));

        assertThatThrownBy(() -> service.asignar(100L,
                new ProjectAssignmentRequest(11L, LocalDate.of(2026, 3, 1), null), gestor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("NX-APP")
                .hasMessageContaining("Ana");

        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    /**
     * El caso que la comprobación previa NO ve: asignar un rango que
     * engloba una asignación futura. La lectura mira el día de inicio, y
     * ese día está libre; quien lo caza es el EXCLUDE de la base.
     */
    @Test
    @DisplayName("El rechazo del EXCLUDE llega como 409 con explicación, no como 500")
    void asignar_solapeQueSoloVeLaBase_seTraduceA409() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(proyecto));
        when(userRepository.findById(11L)).thenReturn(Optional.of(empleado));
        when(assignmentRepository.findVigenteDe(anyLong(), any())).thenReturn(Optional.empty());
        when(assignmentRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException(
                        "could not execute statement",
                        new RuntimeException("conflicting key value violates exclusion constraint "
                                + "\"ex_asignaciones_sin_solape\"")));

        assertThatThrownBy(() -> service.asignar(100L,
                new ProjectAssignmentRequest(11L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                gestor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ana")
                .hasMessageContaining("otro proyecto");
    }

    @Test
    @DisplayName("Otra violación de integridad NO se disfraza de solape")
    void asignar_otraViolacion_seDejaPasar() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(proyecto));
        when(userRepository.findById(11L)).thenReturn(Optional.of(empleado));
        when(assignmentRepository.findVigenteDe(anyLong(), any())).thenReturn(Optional.empty());
        when(assignmentRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("violates foreign key constraint \"fk_otra_cosa\""));

        // Tragarse cualquier DataIntegrityViolation como "ya asignado"
        // convertiría un fallo real en un mensaje tranquilizador y falso.
        assertThatThrownBy(() -> service.asignar(100L,
                new ProjectAssignmentRequest(11L, LocalDate.of(2026, 1, 1), null), gestor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("No se puede asignar a alguien de otra empresa")
    void asignar_personaDeOtraEmpresa_da403() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(proyecto));
        User ajeno = User.builder().id(99L).nombre("Ajeno").empresa(otraEmpresa).build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.asignar(100L,
                new ProjectAssignmentRequest(99L, LocalDate.of(2026, 1, 1), null), gestor))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("Sacar del proyecto cierra la asignación, no la borra")
    void finalizarAsignacion_ponaFechaFinSinBorrar() {
        ProjectAssignment asignacion = ProjectAssignment.builder()
                .id(5L).empresa(empresa).usuario(empleado).proyecto(proyecto)
                .fechaInicio(LocalDate.of(2026, 1, 1)).build();
        when(assignmentRepository.findById(5L)).thenReturn(Optional.of(asignacion));
        when(assignmentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.finalizarAsignacion(5L, LocalDate.of(2026, 6, 30), gestor);

        assertThat(respuesta.fechaFin()).isEqualTo(LocalDate.of(2026, 6, 30));
        // Borrarla haría desaparecer sus horas pasadas de este proyecto.
        verify(assignmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Cerrar una asignación antes de su inicio es un 400")
    void finalizarAsignacion_fechaAnteriorAlInicio_da400() {
        ProjectAssignment asignacion = ProjectAssignment.builder()
                .id(5L).empresa(empresa).usuario(empleado).proyecto(proyecto)
                .fechaInicio(LocalDate.of(2026, 6, 1)).build();
        when(assignmentRepository.findById(5L)).thenReturn(Optional.of(asignacion));

        assertThatThrownBy(() -> service.finalizarAsignacion(5L, LocalDate.of(2026, 1, 1), gestor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Una asignación de otra empresa no se toca")
    void finalizarAsignacion_deOtraEmpresa_da403() {
        ProjectAssignment ajena = ProjectAssignment.builder()
                .id(6L).empresa(otraEmpresa).usuario(empleado).proyecto(proyecto)
                .fechaInicio(LocalDate.of(2026, 1, 1)).build();
        when(assignmentRepository.findById(6L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.finalizarAsignacion(6L, LocalDate.of(2026, 6, 30), gestor))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("Una asignación que no existe es un 404")
    void finalizarAsignacion_inexistente_da404() {
        when(assignmentRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finalizarAsignacion(77L, LocalDate.of(2026, 6, 30), gestor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------
    // Horas
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Los segundos se truncan a minutos, no se redondean")
    void horasDelMes_truncaLosSegundos() {
        when(assignmentRepository.sumarSegundosPorProyecto(anyLong(), any(), any()))
                .thenReturn(List.of(filaDeHoras(100L, "NX-CORE", "Plataforma", 89)));

        var respuesta = service.horasDelMes(2026, 6, gestor);

        // 89 segundos son 1 minuto trabajado, no 2 (ver DashboardServiceImpl).
        assertThat(respuesta.proyectos()).singleElement()
                .satisfies(item -> assertThat(item.minutos()).isEqualTo(1));
    }

    @Test
    @DisplayName("Un mes fuera de 1..12 es un 400, no un 500 de YearMonth")
    void horasDelMes_mesInvalido_da400() {
        assertThatThrownBy(() -> service.horasDelMes(2026, 13, gestor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ProjectAssignmentRepository.ProjectHoursProjection filaDeHoras(
            long id, String codigo, String nombre, long segundos) {
        return new ProjectAssignmentRepository.ProjectHoursProjection() {
            @Override
            public long getProyectoId() {
                return id;
            }

            @Override
            public String getCodigo() {
                return codigo;
            }

            @Override
            public String getNombre() {
                return nombre;
            }

            @Override
            public long getSegundos() {
                return segundos;
            }
        };
    }
}
