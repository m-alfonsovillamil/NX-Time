package com.nxtime.nxtime.dto;

import java.time.Instant;

/**
 * Respuesta de los endpoints de fichaje. Sustituye a exponer la entidad
 * TimeEntry directamente (ver auditoría, defecto #1): antes, al viajar
 * la entidad completa, se arrastraba el Usuario anidado -- y con él, su
 * contraseña cifrada.
 *
 * horaEntrada/horaSalida son Instant desde la Fase 3 (antes
 * LocalDateTime): viajan en JSON como ISO-8601 con sufijo "Z" (UTC),
 * sin ambigüedad de zona horaria. La app cliente los presenta en la
 * zona que corresponda.
 */
public record TimeEntryResponse(
        long id,
        Instant horaEntrada,
        Instant horaSalida,
        boolean enPausa,
        long minutosPausaAcumulados,

        /**
         * Las pausas en SEGUNDOS, tal y como se guardan.
         *
         * Se añade porque minutosPausaAcumulados es un valor truncado
         * ({@code segundos / 60}) y con él no se puede reconstruir el
         * tiempo neto exacto: una pausa de 40 s viaja como 0 minutos, y
         * quien reste ese 0 cuenta 40 s de trabajo que no existieron.
         * Lo destapó el cronómetro en vivo de la app Android, que
         * sobrecontaba justo esa diferencia.
         *
         * minutosPausaAcumulados se mantiene: es lo que se pinta en las
         * listas ("Pausa: 0h 26m") y quitarlo rompería a los clientes ya
         * escritos. Este campo es el que hay que usar para CALCULAR.
         */
        long segundosPausaAcumulados
) {
}
