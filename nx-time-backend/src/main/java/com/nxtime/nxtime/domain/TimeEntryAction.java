package com.nxtime.nxtime.domain;

/**
 * Acción que la app envía al pulsar el botón de fichar.
 *
 * En el Kotlin original, PeticionFichaje.tipo era un String libre y el
 * servicio lo comparaba con un `when` (ver auditoría, defectos de
 * diseño). Aquí pasa a ser un enum real: si llega un valor que no es
 * ninguna de estas 4 constantes, Jackson lo rechaza con un 400 antes de
 * que la petición llegue siquiera al controlador, en vez de que el
 * servicio lo descubra en tiempo de ejecución.
 *
 * Los nombres de las constantes se mantienen en español a propósito
 * (ver Role.java): son el valor real que la app Android envía hoy en
 * el campo "tipo" del JSON.
 */
public enum TimeEntryAction {
    INICIO,
    FIN,
    PAUSA_INICIO,
    PAUSA_FIN
}
