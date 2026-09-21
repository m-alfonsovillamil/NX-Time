package com.nxtime.nxtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Las reglas del reparto por proyecto, en un solo sitio (Fase A3).
 *
 * Estas comprobaciones vivían dentro de {@code AllocationEditServiceImpl} y
 * solo protegían el camino del reparto libre. El de las correcciones, que
 * también acepta un reparto, únicamente miraba que el proyecto fuera de la
 * misma empresa — así que se podían imputar horas a un proyecto en el que
 * nunca se había estado, y al aprobarse la corrección se escribía. Esa es la
 * clase de fallo que no da ningún error: solo informes de coste por proyecto
 * que mienten.
 */
@ExtendWith(MockitoExtension.class)
class ValidadorDeRepartoTest {

    @Mock
    private ProjectAllocationService projectAllocationService;

    private ValidadorDeReparto validador;

    private User ana;
    private TimeEntry jornada;
    private Project nxCore;
    private Project nxWeb;
    private Project ajeno;

    /** 09:00 en hora de España: el día de la jornada no depende de la zona de la JVM. */
    private static final Instant ENTRADA = Instant.parse("2026-06-01T07:00:00Z");

    @BeforeEach
    void setUp() {
        validador = new ValidadorDeReparto(projectAllocationService);

        Company empresa = Company.builder().id(1L).nombre("TechCorp").build();
        ana = User.builder().id(10L).email("ana@test.com").nombre("Ana")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build();
        jornada = TimeEntry.builder().id(5L).usuario(ana).empresa(empresa)
                .horaEntrada(ENTRADA).horaSalida(ENTRADA.plusSeconds(8 * 3600)).build();

        nxCore = Project.builder().id(100L).codigo("NX-CORE").nombre("Core").empresa(empresa).build();
        nxWeb = Project.builder().id(101L).codigo("NX-WEB").nombre("Web").empresa(empresa).build();
        // De la misma empresa, pero Ana no estaba en él ese día: es justo el
        // caso que el camino de las correcciones dejaba pasar.
        ajeno = Project.builder().id(200L).codigo("NX-OTRO").nombre("Otro").empresa(empresa).build();
    }

    private void esosDiaTeniaAsignados(Project... proyectos) {
        when(projectAllocationService.proyectosDelDia(any(), any())).thenReturn(List.of(proyectos));
    }

    @Test
    @DisplayName("Un reparto entre proyectos asignados ese día se resuelve, en segundos")
    void repartoValido_seResuelveEnSegundos() {
        esosDiaTeniaAsignados(nxCore, nxWeb);

        Map<Project, Long> reparto = validador.validarYResolver(jornada,
                List.of(new ValidadorDeReparto.Linea(100L, 300), new ValidadorDeReparto.Linea(101L, 180)));

        assertThat(reparto).containsExactly(
                org.assertj.core.api.Assertions.entry(nxCore, 300L * 60),
                org.assertj.core.api.Assertions.entry(nxWeb, 180L * 60));
    }

    @Test
    @DisplayName("Imputar a un proyecto de la empresa en el que NO se estuvo ese día es 403")
    void proyectoNoAsignadoEseDia_da403() {
        esosDiaTeniaAsignados(nxCore);

        assertThatThrownBy(() -> validador.validarYResolver(jornada,
                List.of(new ValidadorDeReparto.Linea(ajeno.getId(), 480))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no estabas asignado")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Un proyecto inventado da el MISMO 403 que uno ajeno: no se dice cuál existe")
    void proyectoInexistente_daElMismo403() {
        esosDiaTeniaAsignados(nxCore);

        // Distinguir "no existe" de "no era tuyo" convertiría el endpoint en un
        // oráculo sobre los proyectos de la empresa.
        assertThatThrownBy(() -> validador.validarYResolver(jornada,
                List.of(new ValidadorDeReparto.Linea(999_999L, 480))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no estabas asignado");
    }

    @Test
    @DisplayName("El mismo proyecto dos veces es 400: no se adivina si suma o si sustituye")
    void proyectoRepetido_da400() {
        esosDiaTeniaAsignados(nxCore);

        assertThatThrownBy(() -> validador.validarYResolver(jornada,
                List.of(new ValidadorDeReparto.Linea(100L, 200), new ValidadorDeReparto.Linea(100L, 100))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Una línea a cero saca el proyecto del reparto en vez de guardar un cero")
    void lineaACero_seQuita() {
        esosDiaTeniaAsignados(nxCore, nxWeb);

        Map<Project, Long> reparto = validador.validarYResolver(jornada,
                List.of(new ValidadorDeReparto.Linea(100L, 480), new ValidadorDeReparto.Linea(101L, 0)));

        // Guardar el cero solo ensuciaría los informes.
        assertThat(reparto).containsOnlyKeys(nxCore);
    }

    @Test
    @DisplayName("Un reparto entero a cero es 400: eso no es repartir, es borrar")
    void todoACero_da400() {
        esosDiaTeniaAsignados(nxCore);

        assertThatThrownBy(() -> validador.validarYResolver(jornada,
                List.of(new ValidadorDeReparto.Linea(100L, 0))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("todo a cero");
    }

    @Test
    @DisplayName("Pasarse del tope de líneas se corta ANTES de ir a la base")
    void demasiadasLineas_da400SinConsultarAsignaciones() {
        List<ValidadorDeReparto.Linea> muchas = IntStream.rangeClosed(1, ValidadorDeReparto.MAX_LINEAS + 1)
                .mapToObj(i -> new ValidadorDeReparto.Linea(i, 1))
                .toList();

        assertThatThrownBy(() -> validador.validarYResolver(jornada, muchas))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // El mock no está preparado con when(...), así que si se hubiera
        // consultado, MockitoExtension avisaría del stub innecesario. El tope
        // existe precisamente para no recorrer miles de líneas antes de
        // rechazarlas.
    }

    @Test
    @DisplayName("Los proyectos válidos son los del DUEÑO del fichaje, no los de quien corrige")
    void seConsultanLosProyectosDelDuenoDelFichaje() {
        esosDiaTeniaAsignados(nxCore);

        validador.validarYResolver(jornada, List.of(new ValidadorDeReparto.Linea(100L, 480)));

        // Cuando RRHH corrige el fichaje de Ana, lo que cuenta es dónde estaba
        // Ana. Y el día se resuelve en hora de España: a las 07:00 UTC del 1 de
        // junio son las 09:00 del día 1, no del 31 de mayo.
        org.mockito.Mockito.verify(projectAllocationService)
                .proyectosDelDia(ana, LocalDate.of(2026, 6, 1));
    }
}
