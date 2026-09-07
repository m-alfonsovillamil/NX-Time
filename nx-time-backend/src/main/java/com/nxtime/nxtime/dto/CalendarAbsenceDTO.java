package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import java.time.LocalDate;

/**
 * Una ausencia vista desde el calendario: una banda de días con nombre.
 *
 * Es deliberadamente más pobre que {@link AbsenceResponse}:
 * <ul>
 *   <li><b>Sin motivo.</b> Es texto libre que la persona escribe para su
 *       gestor ("operación de rodilla"), y en el calendario del equipo lo
 *       leería cualquiera que pueda ver ausencias ajenas. Para aprobarla
 *       ya está la pantalla de ausencias del equipo, donde sí se ve.</li>
 *   <li><b>Sin la traza de resolución ni los días hábiles.</b> El
 *       calendario solo necesita pintar; quien quiera el detalle abre la
 *       ausencia.</li>
 * </ul>
 *
 * {@code fechaInicio} y {@code fechaFin} son las de la petición completa,
 * no las recortadas al mes que se está mirando: una ausencia del 28 de
 * marzo al 4 de abril tiene que poder pintarse como que viene de antes y
 * sigue después, y recortarla en el servidor haría imposible distinguir
 * eso de una que empieza justo el día 1.
 *
 * {@code propia} evita que el cliente tenga que comparar identificadores
 * de usuario para saber qué pintar como "mío".
 */
public record CalendarAbsenceDTO(
        long id,
        long usuarioId,
        String usuario,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        AbsenceType tipo,
        AbsenceStatus estado,
        boolean propia
) {
}
