package com.nxtime.app.ui.denuncias

import androidx.annotation.StringRes
import com.nxtime.app.R

/**
 * En qué punto está una denuncia, visto desde la app (Fase G).
 *
 * Espeja `ComplaintStatus.java`. [de] devuelve `null` para un valor que
 * esta versión no conozca, y quien llame debe pintarlo como "otro
 * estado" en vez de inventarse uno — mismo criterio que `Rol.de` y
 * `EstadoHorasExtra.de`.
 */
enum class EstadoDenuncia(@param:StringRes val etiqueta: Int) {

    /** Presentada y todavía sin tocar por quien instruye. */
    RECIBIDA(R.string.denuncia_estado_recibida),

    /** Con acuse de recibo dado y en curso. */
    EN_INVESTIGACION(R.string.denuncia_estado_en_investigacion),

    /** Cerrada habiéndose confirmado, con conclusión escrita. */
    RESUELTA(R.string.denuncia_estado_resuelta),

    /** Cerrada sin sostenerse, también con conclusión escrita. */
    ARCHIVADA(R.string.denuncia_estado_archivada);

    /** Si el expediente sigue vivo y los plazos legales corren. */
    val estaAbierta: Boolean
        get() = this == RECIBIDA || this == EN_INVESTIGACION

    companion object {
        fun de(valor: String?): EstadoDenuncia? = entries.firstOrNull { it.name == valor }
    }
}

/**
 * De qué va la denuncia.
 *
 * Espeja `ComplaintCategory.java`. La app necesita la lista **para el
 * desplegable de presentar**, que es lo único que no puede resolver el
 * servidor: en todo lo demás viaja `categoriaEtiqueta` ya en castellano,
 * y por eso una categoría desconocida en un expediente no rompe nada.
 */
enum class CategoriaDenuncia(@param:StringRes val etiqueta: Int) {
    ACOSO(R.string.denuncia_categoria_acoso),
    DISCRIMINACION(R.string.denuncia_categoria_discriminacion),
    FRAUDE(R.string.denuncia_categoria_fraude),
    SEGURIDAD(R.string.denuncia_categoria_seguridad),
    CORRUPCION(R.string.denuncia_categoria_corrupcion),
    PROTECCION_DATOS(R.string.denuncia_categoria_proteccion_datos),
    OTRA(R.string.denuncia_categoria_otra);

    companion object {
        fun de(valor: String?): CategoriaDenuncia? = entries.firstOrNull { it.name == valor }
    }
}

/**
 * De qué lado viene un mensaje del expediente.
 *
 * **Es esto y no `autor == null` lo que decide cómo se pinta.** En una
 * denuncia anónima el mensaje del denunciante llega sin autor, así que
 * mirar el nombre ataría la presentación al anonimato: bastaría con que
 * una denuncia identificada y otra anónima se pintaran distinto para que
 * la pantalla estuviera diciendo cuál es cuál.
 */
enum class AutorMensaje {
    DENUNCIANTE,
    INSTRUCTOR;

    companion object {
        fun de(valor: String?): AutorMensaje? = entries.firstOrNull { it.name == valor }
    }
}
