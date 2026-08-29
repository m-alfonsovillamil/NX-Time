package com.nxtime.app.data.dto

/**
 * Las cuatro acciones de fichaje que acepta el backend.
 *
 * Existía ya como enum en el backend desde la Fase 2 ("TipoFichaje como
 * enum en ambos lados del contrato"), pero en Android se quedó como
 * `String` libre mientras `EstadoAusencia` y `TipoAusencia` sí se
 * convirtieron. Que era una fuente real de fallos lo dejaba escrito un
 * comentario en el propio ViewModel:
 *
 *     // --- ¡AQUÍ ESTÁ LA CORRECCIÓN! ---
 *     // Los strings deben coincidir con el backend
 *
 * Con el enum, una discrepancia de nombre deja de compilar en vez de
 * fallar contra el servidor en tiempo de ejecución.
 */
enum class TipoFichaje {
    INICIO,
    FIN,
    PAUSA_INICIO,
    PAUSA_FIN
}
