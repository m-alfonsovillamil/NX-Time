package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CalendarResponse;
import com.nxtime.nxtime.dto.HolidayRequest;
import com.nxtime.nxtime.dto.HolidayResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.service.HolidayCalendar;
import com.nxtime.nxtime.service.NationalHolidaySeeder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Unitarios del calendario laboral (Fase C).
 *
 * Lo que se prueba aquí no es "pinta un mes", que es aritmética de
 * fechas, sino las tres reglas que si se rompen no se notan mirando la
 * pantalla: que un festivo nacional no se puede tocar desde una empresa,
 * que el calendario del equipo solo llega a quien puede verlo, y que
 * cualquier cambio invalida la caché de días hábiles -- de la que cuelga
 * el saldo de vacaciones de todo el mundo.
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceImplTest {

    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private AbsenceRequestRepository absenceRequestRepository;
    @Mock
    private NationalHolidaySeeder nationalHolidaySeeder;
    @Mock
    private HolidayCalendar holidayCalendar;

    private CalendarServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User empleado;
    private User gestor;

    @BeforeEach
    void setUp() {
        service = new CalendarServiceImpl(
                holidayRepository, absenceRequestRepository, nationalHolidaySeeder, holidayCalendar);

        empresa = Company.builder().id(1L).nombre("TechCorp").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra").build();
        empleado = User.builder().id(10L).email("empleado@test.com")
                .nombre("Ana").rol(Role.EMPLEADO).empresa(empresa).build();
        gestor = User.builder().id(11L).email("gestor@test.com")
                .nombre("Marta").rol(Role.GESTOR).empresa(empresa).build();
    }

    // ---------------------------------------------------------------
    // Ver un mes
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Mirar un mes siembra los festivos nacionales de ese año")
    void verMes_siembraElAnio() {
        cuandoNoHayNadaEnElMes();

        service.verMes(2027, 3, false, empleado);

        // Sin esto, un año que nadie ha mirado nunca sale sin festivos y
        // no parece roto: parece un año sin fiestas.
        verify(nationalHolidaySeeder).asegurarAnio(2027);
    }

    @Test
    @DisplayName("Un empleado no ve las ausencias del equipo aunque las pida")
    void verMes_empleadoPidiendoEquipo_soloVeLasSuyas() {
        cuandoNoHayNadaEnElMes();

        CalendarResponse respuesta = service.verMes(2026, 6, true, empleado);

        assertThat(respuesta.incluyeEquipo()).isFalse();
        verify(absenceRequestRepository).findSolapadas(eq(empleado), any(), any());
        verify(absenceRequestRepository, never()).findSolapadasDeEmpresa(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Un gestor que pide el equipo lo recibe, y sabe que lo ha recibido")
    void verMes_gestorPidiendoEquipo_veATodos() {
        when(holidayRepository.findAplicables(anyLong(), any(), any())).thenReturn(List.of());
        when(absenceRequestRepository.findSolapadasDeEmpresa(anyLong(), any(), any()))
                .thenReturn(List.of(ausenciaDe(empleado)));

        CalendarResponse respuesta = service.verMes(2026, 6, true, gestor);

        assertThat(respuesta.incluyeEquipo()).isTrue();
        assertThat(respuesta.ausencias()).hasSize(1);
        // La ausencia es de otra persona: el cliente no debería tener que
        // comparar ids para saberlo.
        assertThat(respuesta.ausencias().get(0).propia()).isFalse();
        assertThat(respuesta.ausencias().get(0).usuario()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Un gestor que NO pide el equipo ve solo lo suyo")
    void verMes_gestorSinPedirEquipo_soloVeLoSuyo() {
        cuandoNoHayNadaEnElMes();

        CalendarResponse respuesta = service.verMes(2026, 6, false, gestor);

        assertThat(respuesta.incluyeEquipo()).isFalse();
        verify(absenceRequestRepository).findSolapadas(eq(gestor), any(), any());
    }

    @Test
    @DisplayName("Los festivos nacionales llegan marcados como no editables")
    void verMes_marcaQueFestivosSePuedenTocar() {
        when(holidayRepository.findAplicables(anyLong(), any(), any())).thenReturn(List.of(
                Holiday.builder().id(1L).fecha(LocalDate.of(2026, 5, 1))
                        .descripcion("Fiesta del Trabajo").ambito(HolidayScope.NACIONAL).build(),
                Holiday.builder().id(2L).fecha(LocalDate.of(2026, 5, 15)).empresa(empresa)
                        .descripcion("San Isidro").ambito(HolidayScope.LOCAL).build()));
        when(absenceRequestRepository.findSolapadas(any(), any(), any())).thenReturn(List.of());

        List<HolidayResponse> festivos = service.verMes(2026, 5, false, gestor).festivos();

        assertThat(festivos).extracting(HolidayResponse::editable).containsExactly(false, true);
    }

    @Test
    @DisplayName("Un mes fuera de 1..12 es un 400, no un 500 de LocalDate")
    void verMes_mesInvalido_da400() {
        assertThatThrownBy(() -> service.verMes(2026, 13, false, empleado))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Un año absurdo se rechaza antes de sembrarlo")
    void verMes_anioFueraDeRango_niSiquieraSiembra() {
        // Es lo que impide que un bucle pidiendo años deje millones de
        // filas de festivos en la tabla.
        assertThatThrownBy(() -> service.verMes(999_999, 1, false, empleado))
                .isInstanceOf(BusinessException.class);

        verify(nationalHolidaySeeder, never()).asegurarAnio(anyInt());
    }

    // ---------------------------------------------------------------
    // Crear, editar y borrar
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Crear un festivo lo guarda con la empresa del actor e invalida la caché")
    void crear_guardaEInvalidaLaCache() {
        when(holidayRepository.findDeEmpresaEnFecha(anyLong(), any())).thenReturn(Optional.empty());
        when(holidayRepository.save(any(Holiday.class))).thenAnswer(inv -> inv.getArgument(0));

        HolidayResponse respuesta = service.crear(
                new HolidayRequest(LocalDate.of(2026, 5, 15), "  San Isidro  ", HolidayScope.LOCAL),
                gestor);

        assertThat(respuesta.descripcion()).isEqualTo("San Isidro");
        assertThat(respuesta.ambito()).isEqualTo(HolidayScope.LOCAL);
        assertThat(respuesta.editable()).isTrue();
        // El día deja de ser hábil: sin invalidar, el saldo de vacaciones
        // seguiría contándolo hasta seis horas (ver CacheConfig).
        verify(holidayCalendar).invalidar();
    }

    @Test
    @DisplayName("No se puede crear un festivo con ámbito NACIONAL")
    void crear_conAmbitoNacional_da400() {
        assertThatThrownBy(() -> service.crear(
                new HolidayRequest(LocalDate.of(2026, 12, 25), "Navidad", HolidayScope.NACIONAL),
                gestor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dos festivos el mismo día en la misma empresa son un 409")
    void crear_fechaRepetida_da409() {
        when(holidayRepository.findDeEmpresaEnFecha(anyLong(), any())).thenReturn(
                Optional.of(Holiday.builder().id(5L).fecha(LocalDate.of(2026, 5, 15))
                        .descripcion("San Isidro").empresa(empresa).ambito(HolidayScope.LOCAL).build()));

        assertThatThrownBy(() -> service.crear(
                new HolidayRequest(LocalDate.of(2026, 5, 15), "Otro nombre", HolidayScope.EMPRESA),
                gestor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("San Isidro");
    }

    @Test
    @DisplayName("Un festivo nacional no se puede borrar desde una empresa")
    void borrar_festivoNacional_da403() {
        // Es una fila compartida por TODAS las empresas: borrarla desde
        // una se la quitaría del calendario a las demás.
        when(holidayRepository.findById(1L)).thenReturn(Optional.of(
                Holiday.builder().id(1L).fecha(LocalDate.of(2026, 12, 25))
                        .descripcion("Natividad del Señor").ambito(HolidayScope.NACIONAL).build()));

        assertThatThrownBy(() -> service.borrar(1L, gestor))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("nacionales");

        verify(holidayRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Un festivo de otra empresa no se puede tocar (ADR 006)")
    void borrar_festivoDeOtraEmpresa_da403() {
        when(holidayRepository.findById(7L)).thenReturn(Optional.of(
                Holiday.builder().id(7L).fecha(LocalDate.of(2026, 7, 25)).empresa(otraEmpresa)
                        .descripcion("Su día de convenio").ambito(HolidayScope.EMPRESA).build()));

        assertThatThrownBy(() -> service.borrar(7L, gestor))
                .isInstanceOf(TenantAccessException.class);

        verify(holidayRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Borrar uno que no existe es un 404")
    void borrar_inexistente_da404() {
        when(holidayRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrar(99L, gestor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Borrar uno propio lo borra e invalida la caché")
    void borrar_propio_borraEInvalida() {
        Holiday festivo = Holiday.builder().id(3L).fecha(LocalDate.of(2026, 7, 25)).empresa(empresa)
                .descripcion("Día de convenio").ambito(HolidayScope.EMPRESA).build();
        when(holidayRepository.findById(3L)).thenReturn(Optional.of(festivo));

        service.borrar(3L, gestor);

        verify(holidayRepository).delete(festivo);
        verify(holidayCalendar).invalidar();
    }

    @Test
    @DisplayName("Cambiar solo la descripción no choca contra la fecha del propio festivo")
    void editar_mismaFecha_noEsConflicto() {
        Holiday festivo = Holiday.builder().id(3L).fecha(LocalDate.of(2026, 7, 25)).empresa(empresa)
                .descripcion("Día de convenio").ambito(HolidayScope.EMPRESA).build();
        when(holidayRepository.findById(3L)).thenReturn(Optional.of(festivo));
        when(holidayRepository.findDeEmpresaEnFecha(anyLong(), any())).thenReturn(Optional.of(festivo));

        HolidayResponse respuesta = service.editar(3L,
                new HolidayRequest(LocalDate.of(2026, 7, 25), "Cierre de verano", HolidayScope.EMPRESA),
                gestor);

        assertThat(respuesta.descripcion()).isEqualTo("Cierre de verano");
        verify(holidayCalendar).invalidar();
    }

    @Test
    @DisplayName("Mover un festivo encima de otro que ya existe es un 409")
    void editar_moviendoloSobreOtro_da409() {
        Holiday festivo = Holiday.builder().id(3L).fecha(LocalDate.of(2026, 7, 25)).empresa(empresa)
                .descripcion("Día de convenio").ambito(HolidayScope.EMPRESA).build();
        Holiday otro = Holiday.builder().id(4L).fecha(LocalDate.of(2026, 5, 15)).empresa(empresa)
                .descripcion("San Isidro").ambito(HolidayScope.LOCAL).build();
        when(holidayRepository.findById(3L)).thenReturn(Optional.of(festivo));
        when(holidayRepository.findDeEmpresaEnFecha(anyLong(), any())).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> service.editar(3L,
                new HolidayRequest(LocalDate.of(2026, 5, 15), "Da igual", HolidayScope.EMPRESA),
                gestor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("San Isidro");
    }

    // ---------------------------------------------------------------

    private void cuandoNoHayNadaEnElMes() {
        when(holidayRepository.findAplicables(anyLong(), any(), any())).thenReturn(List.of());
        when(absenceRequestRepository.findSolapadas(any(), any(), any())).thenReturn(List.of());
    }

    private AbsenceRequest ausenciaDe(User usuario) {
        return AbsenceRequest.builder()
                .id(50L)
                .usuario(usuario)
                .empresa(empresa)
                .fechaInicio(LocalDate.of(2026, 6, 10))
                .fechaFin(LocalDate.of(2026, 6, 14))
                .tipo(AbsenceType.VACACIONES)
                .estado(AbsenceStatus.APROBADA)
                .motivo("Escapada")
                .build();
    }
}
