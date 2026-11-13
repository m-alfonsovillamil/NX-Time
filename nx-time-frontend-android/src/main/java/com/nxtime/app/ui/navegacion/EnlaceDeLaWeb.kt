package com.nxtime.app.ui.navegacion

/**
 * El destino de un enlace de la web que ha abierto la app (App Links, 1.19).
 *
 * Los botones de los correos llevan a `https://nxtime-web.com/<destino>`, con
 * el mismo destino lógico que el aviso que va con ese correo (`BotonDelCorreo`
 * en el backend). Si Android verifica el dominio, el enlace abre la app en vez
 * del navegador, y aquí se convierte en ese destino para que la app navegue
 * igual que con un push.
 *
 * Devuelve `null` si el enlace no es de la web o si esta versión de la app no
 * conoce el destino: entonces se abre la app sin navegar. Los destinos que el
 * manifiesto declara son solo los que [rutaDeAviso] sabe abrir, así que lo
 * demás (`/legal`, `/ajustes-empresa`…) sigue abriéndose en el navegador.
 */
fun destinoDeEnlaceWeb(host: String?, ruta: String?): String? {
    if (host == null || host.lowercase() !in HOSTS_DE_LA_WEB) return null
    val destino = ruta?.trim('/') ?: return null
    return destino.takeIf { rutaDeAviso(it) != null }
}

/** Solo el dominio de la web: el del `assetlinks.json` que publica. */
val HOSTS_DE_LA_WEB = setOf("nxtime-web.com")

/**
 * Los destinos que abren la app desde un enlace. Tienen que ir también en el
 * `intent-filter` de `MainActivity`, uno por `<data android:path>`: lo
 * comprueba `EnlaceDeLaWebTest`.
 */
val DESTINOS_CON_ENLACE = listOf(
    DESTINO_FICHAR,
    DESTINO_AUSENCIAS,
    DESTINO_AUSENCIAS_EQUIPO_PENDIENTES,
    DESTINO_AUSENCIAS_EQUIPO_RESUELTAS,
    DESTINO_CORRECCIONES_PENDIENTES,
    DESTINO_HISTORIAL,
    DESTINO_HORAS_EXTRA,
    DESTINO_CANAL_DENUNCIAS,
    DESTINO_DENUNCIAS,
    DESTINO_OFERTAS,
    DESTINO_MIS_CANDIDATURAS,
    DESTINO_GESTION_OFERTAS,
    DESTINO_BORRADOS,
    DESTINO_EQUIPO,
    DESTINO_AJUSTES,
    DESTINO_INCIDENCIAS,
    DESTINO_FIRMAS,
    DESTINO_PERFIL
)
