package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.OvertimeAlertResponse;
import com.nxtime.nxtime.dto.OvertimeBalanceResponse;
import com.nxtime.nxtime.dto.ReviewOvertimeRequest;
import java.time.LocalDate;
import java.util.List;

/**
 * Horas extra: detectarlas, revisarlas y llevar la cuenta anual (Fase F).
 *
 * La idea que ordena el resto: <b>lo que detecta el proceso nocturno es
 * un AVISO, no una imputación de horas extra.</b> El reloj sabe que una
 * jornada duró once horas; no sabe si fue una intensiva pactada, un
 * turno partido mal fichado o una guardia. Eso lo decide una persona, y
 * hasta que lo decide el exceso no cuenta para la bolsa del art. 35.2 ET.
 *
 * Por eso hay dos umbrales y no uno:
 *  - <b>Diario</b>: más de 9 h efectivas (art. 34.3 ET). Es un límite
 *    legal fijo, igual para media jornada que para jornada completa.
 *  - <b>Semanal</b>: más de la jornada contratada, <b>prorrateada por
 *    los días hábiles reales de esa semana</b>. Dividir entre cinco a
 *    ciegas convertiría cada puente en una falsa alarma.
 *
 * Ver {@link OvertimeCalculator}, donde vive la aritmética sin base de
 * datos de por medio.
 */
public interface OvertimeService {

    /** Mis avisos de un año. */
    List<OvertimeAlertResponse> mios(User actor, int anio);

    /**
     * Los avisos de la empresa en un año, para quien revisa. Pide
     * "horasextra:revisar".
     *
     * <b>Deja fuera los del propio actor.</b> Esto es "lo que te toca
     * decidir", y sobre lo tuyo no decides nunca; incluirlos pondría en
     * pantalla botones que {@link #revisar} rechaza con 403. Los suyos
     * los tiene en {@link #mios}.
     */
    List<OvertimeAlertResponse> delEquipo(User actor, int anio);

    /**
     * Revisa un aviso: lo justifica (no eran horas extra) o lo acepta
     * (sí lo eran, y descuentan de la bolsa).
     *
     * Nadie revisa lo suyo propio, ni siquiera quien tiene la authority:
     * decidir que las horas extra que hiciste ayer cuentan como tales es
     * exactamente el conflicto de interés que la Fase E ya resolvió para
     * las correcciones.
     */
    OvertimeAlertResponse revisar(long id, ReviewOvertimeRequest request, User actor);

    /**
     * La bolsa anual de una persona. Se calcula al leer, no se guarda:
     * ver {@link com.nxtime.nxtime.dto.OvertimeBalanceResponse}.
     *
     * {@code usuarioId} null significa "la mía". Mirar la de otra
     * persona pide "horasextra:revisar".
     */
    OvertimeBalanceResponse bolsa(User actor, Long usuarioId, int anio);

    /**
     * Recorre un rango de días y crea, actualiza o retira los avisos que
     * correspondan. Lo llama {@code OvertimeScheduler} cada noche.
     *
     * Es idempotente a propósito, y no por elegancia: vuelve a mirar
     * días que ya miró porque una corrección de la Fase E puede haber
     * cambiado una jornada del mes pasado.
     *
     * @return cuántos avisos quedaron abiertos tras la pasada.
     */
    int detectar(LocalDate desde, LocalDate hasta);
}
