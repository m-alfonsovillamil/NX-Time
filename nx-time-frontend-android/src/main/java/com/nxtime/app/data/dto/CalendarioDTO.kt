package com.nxtime.app.data.dto

/**
 * Cuerpo de `GET /api/v1/calendario`: un mes entero, listo para pintar.
 *
 * Festivos y ausencias llegan juntos porque la pantalla no puede dibujar
 * nada hasta tener los dos: separarlos obligaría a coordinar dos
 * peticiones y a decidir qué enseñar cuando solo ha llegado una.
 *
 * @param incluyeEquipo si la respuesta trae ausencias de otras personas.
 *   No es lo mismo que lo que pidió la app: se puede pedir el equipo sin
 *   tener permiso, y entonces llegan solo las propias con este campo a
 *   `false`. Sin él, un mes en el que nadie del equipo está ausente sería
 *   indistinguible de uno al que le han denegado el permiso.
 */
data class CalendarioDTO(
    val anio: Int,
    val mes: Int,
    val incluyeEquipo: Boolean = false,
    val festivos: List<FestivoDTO> = emptyList(),
    val ausencias: List<AusenciaCalendarioDTO> = emptyList()
)

/**
 * Un día festivo.
 *
 * `ambito` viaja como texto y no como enum por el mismo motivo que
 * [AvisoDTO.tipo]: un valor que esta versión de la app no conociera
 * quedaría a `null` sin que Gson avise, y reventaría en el primer `when`
 * que no lo contemple. Se traduce con [com.nxtime.app.ui.calendario.AmbitoFestivo].
 *
 * @param fecha fecha ISO (`2026-05-15`), sin hora: un festivo es un día
 *   de calendario, no un instante.
 * @param editable si es un festivo de tu empresa y por tanto se puede
 *   cambiar o quitar. Los nacionales llegan con `false`: son una fila
 *   compartida por todas las empresas y los pone el sistema. Es condición
 *   necesaria pero no suficiente -- además hace falta poder gestionar el
 *   calendario, que lo dice el rol.
 */
data class FestivoDTO(
    val id: Long,
    val fecha: String,
    val descripcion: String,
    val ambito: String,
    val editable: Boolean = false
)

/**
 * Una ausencia vista desde el calendario: una banda de días con nombre.
 *
 * Llega sin motivo a propósito (lo escribe la persona para su gestor, y
 * aquí lo leería cualquiera que pueda ver ausencias ajenas) y con las
 * fechas de la petición COMPLETA, no recortadas al mes que se está
 * mirando: así se puede pintar que viene de antes o que sigue después.
 */
data class AusenciaCalendarioDTO(
    val id: Long,
    val usuarioId: Long,
    val usuario: String,
    val fechaInicio: String,
    val fechaFin: String,
    val tipo: TipoAusencia,
    val estado: EstadoAusencia,
    val propia: Boolean = false
)

/**
 * Alta y edición de un festivo de la empresa.
 *
 * No lleva empresa: es siempre la de quien hace la petición. Y `ambito`
 * no admite `NACIONAL`; el servidor lo rechaza con un 400 que explica
 * por qué.
 */
data class FestivoRequest(
    val fecha: String,
    val descripcion: String,
    val ambito: String
)
