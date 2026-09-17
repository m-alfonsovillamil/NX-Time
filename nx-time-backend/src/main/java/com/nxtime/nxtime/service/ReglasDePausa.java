package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Qué hace falta para que una pausa añadida a posteriori tenga sentido
 * (ADR 015).
 *
 * Vive aparte y sin dependencias porque la usan los DOS caminos: la vía
 * directa ({@code AddedPauseServiceImpl}) y la de aprobación
 * ({@code CorrectionServiceImpl}). Si cada uno tuviera su copia, la primera
 * regla que se tocara en un sitio y no en el otro dejaría pasar por una
 * puerta lo que la otra rechaza.
 *
 * <p><b>No hay tope de duración</b>, y es una decisión, no un olvido: se
 * pidió así, con motivo siempre obligatorio. Lo que hay aquí no son topes
 * de política sino aritmética: cosas que, si no se cumplen, dejan la
 * jornada en un estado imposible.
 */
public final class ReglasDePausa {

    private ReglasDePausa() {
    }

    /**
     * @param entrada inicio de la jornada (la propuesta, si se corrige a la vez)
     * @param limite  fin de la jornada, o "ahora" si sigue abierta
     * @param segundosYaDePausa lo que la jornada ya lleva de pausa, incluida la
     *     que esté en curso. Parte de ese número NO tiene intervalo (las pausas
     *     fichadas con el botón), así que no se puede comprobar el solape con
     *     ellas: por eso existe la comprobación del total.
     * @param vivas pausas añadidas que siguen en pie en esa jornada
     * @param inicioPausaEnCurso si la jornada está en pausa ahora mismo, desde
     *     cuándo; si no, null
     * @param ahora el reloj, inyectado para poder probarlo
     */
    public static void exigirValida(
            Instant inicio,
            Instant fin,
            Instant entrada,
            Instant limite,
            long segundosYaDePausa,
            List<AddedPause> vivas,
            Instant inicioPausaEnCurso,
            Instant ahora) {

        if (inicio == null || fin == null) {
            throw new BusinessException("Indica cuándo empezó y cuándo acabó la pausa.", HttpStatus.BAD_REQUEST);
        }
        if (!fin.isAfter(inicio)) {
            throw new BusinessException(
                    "La pausa tiene que acabar después de empezar.", HttpStatus.BAD_REQUEST);
        }
        if (fin.isAfter(ahora)) {
            throw new BusinessException("No se puede añadir una pausa que todavía no ha terminado.");
        }
        // Una pausa que se sale de la jornada no es una pausa de esa jornada.
        if (inicio.isBefore(entrada) || fin.isAfter(limite)) {
            throw new BusinessException("La pausa tiene que caber dentro de la jornada.");
        }
        for (AddedPause otra : vivas) {
            if (otra.solapaCon(inicio, fin)) {
                throw new BusinessException("Esa pausa se solapa con otra que ya añadiste en esta jornada.");
            }
        }
        if (inicioPausaEnCurso != null && fin.isAfter(inicioPausaEnCurso)) {
            throw new BusinessException("Esa pausa se solapa con la que tienes en curso ahora mismo.");
        }

        /*
         * El total, y no es redundante con lo de arriba. Las pausas fichadas
         * con el botón no tienen intervalo guardado, así que su solape con la
         * nueva es invisible: esta es la única red que impide que la jornada
         * acabe con más pausa que duración. Los agregados del repositorio
         * restan en SQL sin proteger el resultado, así que un neto negativo
         * saldría tal cual en los informes y en las horas extra.
         */
        long bruto = Duration.between(entrada, limite).getSeconds();
        long nueva = Duration.between(inicio, fin).getSeconds();
        if (segundosYaDePausa + nueva >= bruto) {
            throw new BusinessException(
                    "Con esa pausa la jornada tendría más pausa que tiempo. "
                            + "Revisa las horas o las pausas que ya tenías.");
        }
    }
}
