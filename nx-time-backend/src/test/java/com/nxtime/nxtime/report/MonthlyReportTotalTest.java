package com.nxtime.nxtime.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El total del informe mensual no puede perder minutos por el camino.
 *
 * Este test nace de un bug real: cada fila se truncaba a minutos
 * (`segundos / 60`) y el total sumaba esos valores YA TRUNCADOS. Cada
 * jornada perdía hasta 59 segundos, así que un mes de 22 días laborables
 * podía perder hasta 21 minutos del total — en el PDF que se firma y se
 * entrega ante una inspección de trabajo.
 *
 * Es el mismo error que la auditoría inicial del proyecto detectó en la
 * Fase 2 ({@code Duration.toMinutes()} truncando cada pausa por
 * separado), resuelto entonces acumulando segundos y derivando los
 * minutos una sola vez al final. Se reintrodujo en los informes.
 *
 * La regla, y lo que este test protege: **agregar primero en la unidad
 * exacta, redondear solo al presentar**.
 */
class MonthlyReportTotalTest {

    /** Jornada de {@code horas}h {@code minutos}m {@code segundos}s netos. */
    private ReportRow jornada(int dia, int horas, int minutos, int segundos) {
        long segundosNetos = horas * 3600L + minutos * 60L + segundos;
        return new ReportRow(
                "Ana", LocalDate.of(2026, 6, dia),
                LocalTime.of(9, 0), LocalTime.of(17, 0),
                0, segundosNetos, false);
    }

    private MonthlyReport informe(List<ReportRow> filas) {
        return new MonthlyReport("Empresa", "Ana", YearMonth.of(2026, 6), filas);
    }

    @Test
    @DisplayName("Tres jornadas de 7h59m40s suman 23h59m00s, no 23h58m")
    void total_noPierdeLosSegundosSueltosDeCadaJornada() {
        // 3 x 28780 s = 86340 s = 23 h 59 min exactos.
        // Truncando cada fila: 3 x 479 min = 1437 min = 23 h 57 min.
        MonthlyReport informe = informe(List.of(
                jornada(1, 7, 59, 40),
                jornada(2, 7, 59, 40),
                jornada(3, 7, 59, 40)));

        assertThat(informe.totalSegundos()).isEqualTo(86_340);
        assertThat(informe.totalMinutos()).isEqualTo(1439); // 23h59m
        assertThat(informe.totalLegible()).isEqualTo("23h 59m");
    }

    @Test
    @DisplayName("Un mes de 22 jornadas con 59 s sueltos no pierde 21 minutos")
    void total_mesCompleto_noPierdeMinutos() {
        // El peor caso del bug: cada jornada perdía 59 s al truncarse.
        List<ReportRow> mes = IntStream.rangeClosed(1, 22)
                .mapToObj(dia -> jornada(dia, 8, 0, 59))
                .toList();

        MonthlyReport informe = informe(mes);

        // 22 x 28859 s = 634898 s = 10581 min (con el bug: 22 x 480 = 10560).
        assertThat(informe.totalSegundos()).isEqualTo(634_898);
        assertThat(informe.totalMinutos()).isEqualTo(10_581);
    }

    @Test
    @DisplayName("Cada fila sigue mostrándose redondeada hacia abajo: 7h59m40s se ve como 7h 59m")
    void filaIndividual_sePresentaTruncada() {
        // Truncar al PRESENTAR una fila es correcto: nadie espera ver
        // segundos en un informe de jornada. Lo que no vale es truncar
        // ANTES de sumar.
        ReportRow fila = jornada(1, 7, 59, 40);

        assertThat(fila.minutosNetos()).isEqualTo(479);
        assertThat(fila.duracionLegible()).isEqualTo("7h 59m");
    }

    @Test
    @DisplayName("Un informe sin filas da cero, no falla")
    void informeVacio_totalCero() {
        MonthlyReport vacio = informe(List.of());

        assertThat(vacio.totalSegundos()).isZero();
        assertThat(vacio.totalMinutos()).isZero();
        assertThat(vacio.totalLegible()).isEqualTo("0h 00m");
    }

    @Test
    @DisplayName("El total en horas y minutos se formatea con el minuto a dos dígitos")
    void totalLegible_formato() {
        MonthlyReport informe = informe(List.of(jornada(1, 7, 5, 0)));

        assertThat(informe.totalLegible()).isEqualTo("7h 05m");
    }
}
