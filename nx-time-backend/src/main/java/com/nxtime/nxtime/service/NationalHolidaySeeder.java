package com.nxtime.nxtime.service;

import com.nxtime.nxtime.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda los festivos nacionales de un año la primera vez que alguien
 * mira ese año (Fase C).
 *
 * <b>Por qué al leer y no al arrancar.</b> Sembrar en el arranque
 * obligaría a decidir cuántos años por delante, y la respuesta correcta
 * cambia con la fecha: un servidor levantado en noviembre de 2026 y no
 * reiniciado hasta marzo dejaría enero, febrero y el Viernes Santo de
 * 2027 sin festivos. Al hacerlo bajo demanda, el año existe en cuanto
 * alguien lo mira, incluido el que se consulta por curiosidad tres años
 * más adelante.
 *
 * <b>Por qué es un bean aparte y con {@code REQUIRES_NEW}.</b> Dos
 * motivos que van juntos:
 * <ul>
 *   <li>Quien llama es una LECTURA ({@code GET /api/v1/calendario}),
 *       cuya transacción es {@code readOnly}. Un INSERT dentro de ella
 *       lo rechaza Hibernate. {@code REQUIRES_NEW} abre una transacción
 *       propia, de escritura, que confirma por su cuenta.</li>
 *   <li>La propagación la aplica el proxy de Spring, así que una llamada
 *       desde otro método de la MISMA clase se la saltaría en silencio
 *       -- es la misma trampa que documenta {@link HolidayCalendar}.</li>
 * </ul>
 */
@Component
public class NationalHolidaySeeder {

    private static final Logger log = LoggerFactory.getLogger(NationalHolidaySeeder.class);

    private final HolidayRepository holidayRepository;
    private final HolidayCalendar holidayCalendar;

    public NationalHolidaySeeder(HolidayRepository holidayRepository, HolidayCalendar holidayCalendar) {
        this.holidayRepository = holidayRepository;
        this.holidayCalendar = holidayCalendar;
    }

    /**
     * Deja sembrados los festivos nacionales del año si no lo estaban.
     *
     * Es idempotente y se puede llamar en cada petición: cuando ya
     * están, el coste es un {@code COUNT} sobre una tabla de decenas de
     * filas.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void asegurarAnio(int anio) {
        if (holidayRepository.existenNacionalesDelAnio(anio)) {
            return;
        }

        try {
            holidayRepository.saveAll(NationalHolidayGenerator.delAnio(anio));
            // Lo que acaba de entrar cambia los días hábiles de ese año,
            // y el calendario cacheado de CADA empresa los incluye (un
            // nacional aplica a todas). Sin esto, quien hubiera mirado el
            // año antes de la siembra seguiría contando sus vacaciones
            // sin festivos hasta seis horas después.
            holidayCalendar.invalidar();
            log.info("Sembrados los festivos nacionales de {}", anio);
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones a la vez sobre un año recién estrenado:
            // ambas pasan el COUNT y la segunda choca contra
            // uq_festivos_nacional_fecha. No es un error -- el trabajo
            // está hecho, lo hizo la otra. Se traga aquí y no más arriba
            // porque es el único sitio que sabe que ese choque concreto
            // significa "ya sembrado" y no "datos corruptos".
            log.debug("Los festivos nacionales de {} ya los sembró otra petición", anio);
            holidayCalendar.invalidar();
        }
    }
}
