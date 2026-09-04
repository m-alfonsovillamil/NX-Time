package com.nxtime.app.data.dto

/**
 * Un aviso dentro de la aplicación.
 *
 * `tipo` viaja como texto y no como enum a propósito: si el backend
 * añade un tipo que esta versión de la app no conoce (y va a pasar --
 * las fases siguientes traen avisos de correcciones y de horas extra),
 * Gson pondría `null` en un enum sin avisar de nada, y el `null` acaba
 * reventando en el primer `when` que no lo contemple. Con un `String`
 * el aviso se sigue leyendo entero.
 *
 * @param rutaDestino destino LÓGICO ("ausencias", "ausencias-equipo/pendientes"),
 *   no una ruta de este grafo de navegación: lo traduce
 *   [com.nxtime.app.ui.navegacion.rutaDeAviso]. Null cuando el aviso
 *   solo informa.
 * @param creadoEn instante ISO-8601 en UTC, como el resto de fechas de
 *   la API.
 */
data class AvisoDTO(
    val id: Long,
    val tipo: String,
    val titulo: String,
    val cuerpo: String,
    val rutaDestino: String? = null,
    val leido: Boolean = false,
    val creadoEn: String
)

/** Cuerpo de `GET /api/v1/avisos/no-leidos`: el número de la campana. */
data class ContadorAvisosDTO(val noLeidos: Int = 0)
