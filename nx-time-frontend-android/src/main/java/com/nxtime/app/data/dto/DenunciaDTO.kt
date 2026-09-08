package com.nxtime.app.data.dto

/**
 * Presentar una denuncia en el canal interno (Fase G).
 *
 * `anonima` no tiene valor por defecto **ni aquí ni en el servidor**, y
 * es la única petición de toda la app en la que eso es deliberado: un
 * defecto a false convertiría en delator a quien solo se dejó un campo,
 * y el anonimato no se puede deshacer después.
 */
data class CrearDenunciaRequest(
    /** "ACOSO", "DISCRIMINACION", "FRAUDE"... Ver [CategoriaDenuncia]. */
    val categoria: String,
    val descripcion: String,
    val anonima: Boolean
)

/**
 * La respuesta a presentar una denuncia: **la única vez que el código de
 * seguimiento existe fuera del móvil de quien denuncia**.
 *
 * El servidor solo guarda su hash, así que no hay endpoint que lo
 * reenvíe y no lo va a haber: poder recuperarlo sería poder demostrar
 * que una denuncia anónima es de alguien.
 *
 * `avisoImportante` viaja desde el servidor en vez de estar en
 * `strings.xml` a propósito. Si dependiera de que cada cliente se
 * acuerde de enseñarlo, el primero que lo olvide deja a una persona sin
 * acceso a su propio expediente para siempre.
 */
data class DenunciaCreadaDTO(
    val codigoSeguimiento: String,
    val anonima: Boolean,
    val creadoEn: String,
    val avisoImportante: String
)

/**
 * Un expediente completo: la denuncia y su conversación.
 *
 * Lo devuelven las dos puertas del canal —la del código y la de quien
 * instruye— y devuelven lo mismo, así que la app tiene un solo tipo.
 *
 * `denunciante` es null cuando la denuncia es anónima, y entonces no hay
 * ningún otro campo del que salga esa identidad: tampoco de los
 * mensajes.
 *
 * `diasHastaAcuse` y `diasHastaRespuesta` son los dos plazos del art.
 * 9.2 de la Ley 2/2023 (7 días naturales y 3 meses). Llegan **en
 * negativo** si el plazo ya se pasó —que es lo que hay que enseñar, no
 * esconder tras un cero— y a null cuando su hecho ya ocurrió.
 *
 * `categoria` y `estado` viajan como texto y no como enum por lo mismo
 * que [AvisoDTO.tipo]: un valor que esta versión no conozca quedaría a
 * `null` sin que Gson avise.
 */
data class DenunciaDTO(
    val id: Long,
    val categoria: String,
    /** La categoría ya en castellano, resuelta por el servidor. */
    val categoriaEtiqueta: String,
    val descripcion: String,
    val estado: String,
    val anonima: Boolean,
    val denunciante: String? = null,
    val creadoEn: String,
    val acuseReciboEn: String? = null,
    val resueltaEn: String? = null,
    val conclusion: String? = null,
    val diasHastaAcuse: Long? = null,
    val diasHastaRespuesta: Long? = null,
    val mensajes: List<MensajeDenunciaDTO> = emptyList()
)

/**
 * Una fila de la bandeja del canal, o de "mis denuncias".
 *
 * **No lleva la descripción**, y no es por ahorrar bytes: una lista se
 * mira de refilón, a veces con alguien detrás, y el relato de un acoso
 * no es algo que deba aparecer en una vista de conjunto. Para leerlo hay
 * que abrir el expediente, que es un gesto deliberado.
 */
data class ResumenDenunciaDTO(
    val id: Long,
    val categoria: String,
    val categoriaEtiqueta: String,
    val estado: String,
    val anonima: Boolean,
    val creadoEn: String,
    val acuseReciboEn: String? = null,
    val diasHastaAcuse: Long? = null,
    val diasHastaRespuesta: Long? = null,
    val mensajes: Int = 0
)

/**
 * Un mensaje del expediente.
 *
 * `autor` es null en los del denunciante cuando la denuncia es anónima,
 * y la pantalla **no lo mira** para decidir de qué lado pintarlo: eso lo
 * dice `autorRol`. Atar la presentación al anonimato es como se acaba
 * enseñando "Anónimo" en un sitio y un nombre en otro.
 */
data class MensajeDenunciaDTO(
    val id: Long,
    /** "DENUNCIANTE" o "INSTRUCTOR". */
    val autorRol: String,
    val autor: String? = null,
    val texto: String,
    val creadoEn: String
)

/** Escribir en el expediente, por cualquiera de las dos puertas. */
data class MensajeDenunciaRequest(val texto: String)

/**
 * Mover una denuncia de estado.
 *
 * La conclusión es obligatoria al cerrar (RESUELTA o ARCHIVADA) y está
 * prohibida sin cerrar: las dos cosas las rechaza el servidor con un
 * 400, y la segunda además la impide un CHECK de la base.
 */
data class CambiarEstadoDenunciaRequest(
    val estado: String,
    val conclusion: String? = null
)
