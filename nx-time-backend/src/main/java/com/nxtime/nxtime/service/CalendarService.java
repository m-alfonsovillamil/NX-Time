package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CalendarResponse;
import com.nxtime.nxtime.dto.HolidayRequest;
import com.nxtime.nxtime.dto.HolidayResponse;

/**
 * El calendario laboral: qué días son festivos y quién está ausente
 * (Fase C).
 *
 * Leerlo lo puede cualquiera con sesión ({@code calendario:leer});
 * cambiar los festivos exige {@code calendario:gestionar}, y solo sobre
 * los de la propia empresa -- los nacionales son una fila compartida por
 * todas y los siembra el sistema (ver
 * {@link com.nxtime.nxtime.domain.HolidayScope}).
 */
public interface CalendarService {

    /**
     * Un mes de calendario: sus festivos y las ausencias que lo tocan.
     *
     * @param verEquipo si se piden también las ausencias de los
     *   compañeros. Se atiende solo si quien pregunta tiene
     *   {@code ausencia:leer:equipo}; si no, se devuelven las propias
     *   sin fallar, y la respuesta lo dice en {@code incluyeEquipo}.
     *   Un 403 aquí obligaría al cliente a saber su propio rol antes de
     *   pedir el mes.
     */
    CalendarResponse verMes(int anio, int mes, boolean verEquipo, User actor);

    /** Da de alta un festivo de la empresa del actor. */
    HolidayResponse crear(HolidayRequest request, User actor);

    /** Cambia la fecha, la descripción o el ámbito de uno ya existente. */
    HolidayResponse editar(long id, HolidayRequest request, User actor);

    /** Borra un festivo de la empresa del actor. */
    void borrar(long id, User actor);
}
