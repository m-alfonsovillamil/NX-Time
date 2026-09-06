package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.HolidayScope;
import java.time.LocalDate;

/**
 * Un día festivo, tal y como se pinta en el calendario.
 *
 * {@code editable} dice si esta fila pertenece a la empresa de quien
 * pregunta y, por tanto, se puede modificar o borrar desde la
 * aplicación. Los nacionales son una única fila compartida por todas las
 * empresas (ver {@link HolidayScope}), así que nadie los toca por API y
 * llegan con {@code false}.
 *
 * Es una condición NECESARIA, no suficiente: además hace falta la
 * authority {@code calendario:gestionar}, que el cliente ya conoce por
 * el rol. Viaja de todas formas porque el rol no basta para saberlo --
 * un GESTOR con la authority tampoco puede borrar Navidad.
 */
public record HolidayResponse(
        long id,
        LocalDate fecha,
        String descripcion,
        HolidayScope ambito,
        boolean editable
) {
}
