package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Los festivos nacionales de España de un año, calculados (Fase C).
 *
 * <b>Por qué calcularlos y no teclearlos.</b> Nueve de los diez son
 * fechas fijas del calendario laboral estatal (art. 37.2 ET y el RD
 * anual que lo desarrolla) y el décimo, el Viernes Santo, se deduce de
 * la Pascua. Una tabla escrita a mano habría que rellenarla cada
 * diciembre, y el año que nadie se acordara el calendario aparecería
 * vacío sin dar ningún aviso -- el peor fallo posible aquí, porque un
 * calendario vacío no parece roto, parece un año sin fiestas.
 *
 * <b>Lo que este cálculo NO puede saber</b>, y por eso el resultado es
 * una base editable y no la última palabra:
 * <ul>
 *   <li>Cuando un festivo estatal cae en domingo, el traslado al lunes
 *       lo decide el Gobierno año por año en el BOE. Aquí la fecha se
 *       deja donde cae: mover el día por nuestra cuenta sería inventarse
 *       una norma que puede no existir ese año.</li>
 *   <li>Las comunidades autónomas sustituyen algunas de estas fiestas
 *       por otras propias, y cada municipio añade dos locales. Eso entra
 *       como {@link HolidayScope#AUTONOMICO} o {@link HolidayScope#LOCAL}
 *       desde la aplicación, que es lo que sí sabe el gestor.</li>
 * </ul>
 * La pantalla lo dice con esas palabras: prometer "todos los festivos"
 * sería falso, y alguien plantearía sus vacaciones contando con ello.
 *
 * Es una clase sin estado y sin dependencias a propósito: el cálculo se
 * puede probar con un {@code assertEquals} por año, sin base de datos ni
 * contexto de Spring. Quien los guarda es {@link NationalHolidaySeeder}.
 */
public final class NationalHolidayGenerator {

    private NationalHolidayGenerator() {
    }

    /**
     * Las nueve fechas fijas, en orden de calendario.
     *
     * Un {@code LinkedHashMap} y no un {@code Map.of}: este último no
     * garantiza el orden de iteración, y la lista sale de aquí para
     * pintarse debajo del calendario.
     */
    private static final Map<MonthDay, String> FIJOS = new LinkedHashMap<>();

    static {
        FIJOS.put(MonthDay.of(1, 1), "Año Nuevo");
        FIJOS.put(MonthDay.of(1, 6), "Epifanía del Señor");
        FIJOS.put(MonthDay.of(5, 1), "Fiesta del Trabajo");
        FIJOS.put(MonthDay.of(8, 15), "Asunción de la Virgen");
        FIJOS.put(MonthDay.of(10, 12), "Fiesta Nacional de España");
        FIJOS.put(MonthDay.of(11, 1), "Todos los Santos");
        FIJOS.put(MonthDay.of(12, 6), "Día de la Constitución Española");
        FIJOS.put(MonthDay.of(12, 8), "Inmaculada Concepción");
        FIJOS.put(MonthDay.of(12, 25), "Natividad del Señor");
    }

    /**
     * Los diez festivos nacionales del año, ordenados por fecha.
     *
     * Vuelven como {@link Holiday} recién construidos, sin id y sin
     * empresa ({@link HolidayScope#NACIONAL} obliga a que no la tengan,
     * ver {@code ck_festivos_ambito_coherente}). No están guardados:
     * quien los persiste decide cuándo.
     */
    public static List<Holiday> delAnio(int anio) {
        List<Holiday> festivos = new ArrayList<>(FIJOS.size() + 1);

        FIJOS.forEach((diaYMes, descripcion) ->
                festivos.add(nacional(diaYMes.atYear(anio), descripcion)));

        festivos.add(nacional(viernesSanto(anio), "Viernes Santo"));

        festivos.sort((uno, otro) -> uno.getFecha().compareTo(otro.getFecha()));
        return festivos;
    }

    /**
     * El Viernes Santo: dos días antes del Domingo de Resurrección.
     *
     * Se resta sobre la fecha, no sobre el número de día del mes: en
     * 2024 la Pascua cayó el 31 de marzo y el Viernes Santo fue el 29
     * del mismo mes, pero en un año en que la Pascua caiga el 1 o el 2
     * de abril el Viernes Santo está en marzo, y restar al día del mes
     * daría un "0 de abril".
     */
    public static LocalDate viernesSanto(int anio) {
        return domingoDePascua(anio).minusDays(2);
    }

    /**
     * Domingo de Resurrección por el <b>algoritmo gregoriano anónimo</b>
     * (Meeus/Jones/Butcher).
     *
     * La Pascua es el primer domingo tras la primera luna llena
     * eclesiástica posterior al equinoccio de marzo, con la luna
     * aproximada por tablas y no por astronomía real: no hay una fórmula
     * "natural" que sacar, es una convención litúrgica, y este algoritmo
     * es su forma cerrada. Por eso las variables se llaman como en la
     * literatura ({@code a}, {@code b}, {@code h}...) en lugar de tener
     * nombres inventados: no significan nada por separado, y bautizarlas
     * "cicloLunar" o "correccionSolar" daría una falsa sensación de que
     * se pueden leer una a una.
     *
     * Toda la aritmética es entera y las divisiones son truncadas a
     * propósito -- es parte del algoritmo, no un descuido.
     */
    public static LocalDate domingoDePascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }

    private static Holiday nacional(LocalDate fecha, String descripcion) {
        return Holiday.builder()
                .fecha(fecha)
                .descripcion(descripcion)
                .ambito(HolidayScope.NACIONAL)
                .build();
    }
}
