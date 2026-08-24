package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.TimeEntryAction;

/**
 * La app envía esto al backend al pulsar el botón de fichar.
 *
 * En el Kotlin original "tipo" era un String libre; aquí es un enum (ver
 * TimeEntryAction). Los valores válidos en el JSON no cambian
 * (INICIO/FIN/PAUSA_INICIO/PAUSA_FIN): un valor distinto ahora es
 * rechazado por Jackson al deserializar, en vez de llegar al servicio.
 */
public record TimeEntryRequest(TimeEntryAction tipo) {
}
