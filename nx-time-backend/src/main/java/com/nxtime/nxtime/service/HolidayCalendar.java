package com.nxtime.nxtime.service;

import com.nxtime.nxtime.config.CacheConfig;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.repository.HolidayRepository;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Festivos de una empresa y un año, cacheados (Fase 10).
 *
 * Resuelve el N+1 sobre "festivos" que quedó anotado en la Fase 9:
 * montar un listado de ausencias calculaba los días hábiles de cada
 * petición, y cada cálculo consultaba el calendario. Ahora todas esas
 * llamadas comparten la misma entrada de caché.
 *
 * Es una clase aparte, y no un método más de {@link
 * com.nxtime.nxtime.service.impl.WorkingDayServiceImpl}, por un motivo
 * concreto: {@code @Cacheable} funciona a través del proxy que crea
 * Spring, así que una llamada de un método de la clase a otro método de
 * la MISMA clase se salta el proxy y, con él, la caché -- silenciosamente.
 * Al vivir en otro bean, la llamada pasa sí o sí por el proxy.
 *
 * La clave es (empresa, año) y no el rango de fechas pedido: un rango
 * arbitrario sería distinto en cada llamada y no se reutilizaría nunca.
 */
@Component
public class HolidayCalendar {

    private final HolidayRepository holidayRepository;

    public HolidayCalendar(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Cacheable(cacheNames = CacheConfig.FESTIVOS, key = "#empresaId + ':' + #anio")
    @Transactional(readOnly = true)
    public Set<LocalDate> festivosDelAnio(long empresaId, int anio) {
        return holidayRepository.findByEmpresaYAnio(empresaId, anio).stream()
                .map(Holiday::getFecha)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Invalida el calendario cacheado de una empresa. Hoy los festivos
     * solo los siembra {@code DemoDataSeeder} al arrancar, así que no
     * hay nadie que los cambie en caliente; existe para que, cuando se
     * añada el alta de festivos por API, quede claro dónde hay que
     * invalidar y no se descubra tarde que el calendario se quedó viejo
     * seis horas.
     */
    @CacheEvict(cacheNames = CacheConfig.FESTIVOS, allEntries = true)
    public void invalidar() {
        // Solo evicción: el trabajo lo hace la anotación.
    }
}
