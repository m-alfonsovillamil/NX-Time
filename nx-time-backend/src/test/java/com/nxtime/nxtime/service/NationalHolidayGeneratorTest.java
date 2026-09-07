package com.nxtime.nxtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unitarios del calendario nacional (Fase C).
 *
 * El grueso de los casos va sobre la Pascua, y no por gusto: las nueve
 * fechas fijas son una lista que se lee de un vistazo, mientras que el
 * Viernes Santo sale de un algoritmo de una docena de líneas de
 * aritmética entera donde una división truncada de menos pasa
 * desapercibida y desplaza el festivo un mes entero.
 */
class NationalHolidayGeneratorTest {

    /**
     * Fechas contrastadas del Domingo de Resurrección.
     *
     * Están elegidas para cubrir los extremos del algoritmo, no al azar:
     * 2008 es la Pascua más temprana en mucho tiempo (23 de marzo) y
     * 2038 la más tardía del rango que acepta la aplicación (25 de
     * abril), que son justo los años donde un error de redondeo se sale
     * de mes. Los intermedios son años recientes comprobables contra
     * cualquier calendario.
     */
    @ParameterizedTest(name = "La Pascua de {0} es el {1}")
    @CsvSource({
            "2008, 2008-03-23",
            "2020, 2020-04-12",
            "2024, 2024-03-31",
            "2025, 2025-04-20",
            "2026, 2026-04-05",
            "2027, 2027-03-28",
            "2038, 2038-04-25"
    })
    void domingoDePascua_devuelveLaFechaConocida(int anio, LocalDate esperada) {
        assertThat(NationalHolidayGenerator.domingoDePascua(anio)).isEqualTo(esperada);
    }

    @ParameterizedTest(name = "La Pascua de {0} cae en domingo")
    @ValueSource(ints = {2000, 2011, 2026, 2050, 2077, 2100})
    @DisplayName("Sea cual sea el año, la Pascua cae en domingo")
    void domingoDePascua_siempreEsDomingo(int anio) {
        // Es la comprobación que atrapa un fallo del algoritmo sin tener
        // que conocer la fecha de antemano: la Pascua es por definición
        // un domingo, así que cualquier resultado que caiga en martes es
        // un error aunque el día y el mes parezcan razonables.
        assertThat(NationalHolidayGenerator.domingoDePascua(anio).getDayOfWeek())
                .isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("El Viernes Santo son dos días antes de la Pascua, aunque cambie de mes")
    void viernesSanto_cruzandoElCambioDeMes() {
        // En 2024 la Pascua fue el domingo 31 de marzo: restar dos al
        // día del mes en vez de a la fecha habría dado el "29" correcto
        // por casualidad, así que el año que lo destapa es otro.
        assertThat(NationalHolidayGenerator.viernesSanto(2024)).isEqualTo(LocalDate.of(2024, 3, 29));
        // 2018: Pascua el 1 de abril, Viernes Santo el 30 de MARZO.
        assertThat(NationalHolidayGenerator.viernesSanto(2018)).isEqualTo(LocalDate.of(2018, 3, 30));
    }

    @ParameterizedTest(name = "El Viernes Santo de {0} cae en viernes")
    @ValueSource(ints = {2024, 2025, 2026, 2027, 2030})
    void viernesSanto_siempreEsViernes(int anio) {
        assertThat(NationalHolidayGenerator.viernesSanto(anio).getDayOfWeek())
                .isEqualTo(DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("Un año trae los diez festivos nacionales, todos de ese año")
    void delAnio_devuelveDiezFestivosDelAnioPedido() {
        List<Holiday> festivos = NationalHolidayGenerator.delAnio(2026);

        assertThat(festivos).hasSize(10);
        assertThat(festivos).allSatisfy(festivo ->
                assertThat(festivo.getFecha().getYear()).isEqualTo(2026));
    }

    @Test
    @DisplayName("Vienen ordenados por fecha, con el Viernes Santo en su sitio")
    void delAnio_vieneOrdenadoPorFecha() {
        List<Holiday> festivos = NationalHolidayGenerator.delAnio(2026);

        assertThat(festivos).extracting(Holiday::getFecha).isSorted();
        // El Viernes Santo de 2026 es el 3 de abril: entre el 1 de enero
        // y el 1 de mayo, o sea que el orden no es simplemente el de la
        // lista de fechas fijas.
        assertThat(festivos.get(2).getFecha()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(festivos.get(2).getDescripcion()).isEqualTo("Viernes Santo");
    }

    @Test
    @DisplayName("Son NACIONALES y sin empresa: lo que exige el CHECK de la base")
    void delAnio_sonNacionalesYSinEmpresa() {
        // ck_festivos_ambito_coherente rechaza un NACIONAL con empresa,
        // así que si esto se rompiera la siembra fallaría entera al
        // primer INSERT, y encima dentro de un GET.
        assertThat(NationalHolidayGenerator.delAnio(2026)).allSatisfy(festivo -> {
            assertThat(festivo.getAmbito()).isEqualTo(HolidayScope.NACIONAL);
            assertThat(festivo.getEmpresa()).isNull();
        });
    }

    @Test
    @DisplayName("No se inventa el traslado de un festivo que cae en domingo")
    void delAnio_noTrasladaLosQueCaenEnDomingo() {
        // El 1 de noviembre de 2026 es domingo. Si algún año el Gobierno
        // lo traslada al lunes, lo dirá el BOE y lo añadirá el gestor: el
        // generador deja la fecha donde cae (ver NationalHolidayGenerator).
        assertThat(LocalDate.of(2026, 11, 1).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(NationalHolidayGenerator.delAnio(2026))
                .extracting(Holiday::getFecha)
                .contains(LocalDate.of(2026, 11, 1))
                .doesNotContain(LocalDate.of(2026, 11, 2));
    }
}
