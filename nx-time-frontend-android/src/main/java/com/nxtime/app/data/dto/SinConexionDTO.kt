package com.nxtime.app.data.dto

/**
 * Un fichaje hecho sin conexión, guardado en el móvil hasta que vuelva la red
 * (ADR 054). Aquí, en `data.dto`, para que lo cubran las reglas de R8: se guarda
 * con Gson.
 *
 * @param dueno el correo de quien fichó: nunca se manda con la sesión de otro
 * @param horaDelDispositivo la hora del reloj del móvil al pulsar, en ISO-8601
 * @param elapsedAlFichar `elapsedRealtime` al pulsar: el reloj que no se puede cambiar a mano
 * @param arranqueAlFichar `Settings.Global.BOOT_COUNT` al pulsar: si cambia, el móvil se reinició
 */
data class FichajePendiente(
    val idCliente: String,
    val dueno: String,
    val tipo: TipoFichaje,
    val proyectoId: Long? = null,
    val modalidad: Modalidad? = null,
    val ubicacion: UbicacionDelFichaje? = null,
    val horaDelDispositivo: String,
    val elapsedAlFichar: Long,
    val arranqueAlFichar: Int
)

/** Cuerpo de `POST /api/v1/fichaje/sin-conexion`: hasta 50, en el orden en que se hicieron. */
data class EnvioSinConexionRequest(val fichajes: List<FichajeSinConexion>)

/** @param msDesdeElFichaje null si el móvil se ha reiniciado: esa cuenta empieza de cero al arrancar */
data class FichajeSinConexion(
    val idCliente: String,
    val tipo: TipoFichaje,
    val proyectoId: Long? = null,
    val horaDelDispositivo: String,
    val msDesdeElFichaje: Long? = null,
    val modalidad: Modalidad? = null,
    val ubicacion: UbicacionDelFichaje? = null
)

/** `resultado` como texto, como `CorreccionDTO.estado`: un valor nuevo no se pierde sin avisar. */
data class ResultadoSinConexion(
    val idCliente: String,
    val resultado: String,
    val yaRecibido: Boolean = false,
    val motivo: String? = null,
    val registroId: Long? = null,
    val hora: String? = null
)

/** @param jornada la jornada abierta después de aplicarlos, o null si no queda ninguna */
data class EnvioSinConexionResponse(
    val resultados: List<ResultadoSinConexion> = emptyList(),
    val jornada: Registro? = null
)

/** Lo que hay que enseñar de un fichaje que no entró tal cual: rechazado, o a revisar por llegar tarde. */
data class AvisoDeEnvio(
    val idCliente: String,
    val dueno: String,
    val tipo: TipoFichaje,
    val horaDelDispositivo: String,
    val aRevisar: Boolean,
    val motivo: String?
)

/**
 * El servidor ha rechazado el envío entero (un 4xx que no se arregla esperando):
 * se sigue intentando, pero la persona tiene que saberlo. `detalle` es el
 * `detail` del ProblemDetail, si lo había.
 */
data class ErrorDelEnvio(val codigo: Int, val detalle: String?)
