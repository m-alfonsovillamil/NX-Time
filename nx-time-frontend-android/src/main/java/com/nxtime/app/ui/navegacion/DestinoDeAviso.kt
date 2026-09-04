package com.nxtime.app.ui.navegacion

/**
 * Traduce el destino lógico que trae un aviso a una ruta de ESTE grafo
 * de navegación.
 *
 * El servidor guarda símbolos ("ausencias", "ausencias-equipo/pendientes")
 * y no rutas de Compose, por dos razones que se explican largo en
 * `V6__avisos.sql`: un aviso vive en la base indefinidamente y
 * sobreviviría a cualquier renombrado de [Pantalla], y codificar
 * argumentos de Navigation desde Java repetiría el problema del `+` que
 * ya rompió la pantalla de corrección una vez.
 *
 * Devuelve `null` cuando el aviso no lleva a ninguna parte **o cuando
 * esta versión de la app no conoce el símbolo**. Lo segundo va a pasar
 * en cuanto el backend desplegado vaya por delante de la app instalada,
 * y la degradación correcta es que el aviso se lea igual pero no
 * navegue: lanzar una excepción convertiría un aviso informativo en un
 * cierre de la aplicación.
 */
fun rutaDeAviso(rutaDestino: String?): String? = when (rutaDestino) {
    DESTINO_FICHAR -> Pantalla.FICHAR.ruta
    DESTINO_AUSENCIAS -> Pantalla.AUSENCIAS.ruta
    DESTINO_AUSENCIAS_EQUIPO_PENDIENTES -> Pantalla.ausenciasEquipo(resueltas = false)
    DESTINO_AUSENCIAS_EQUIPO_RESUELTAS -> Pantalla.ausenciasEquipo(resueltas = true)
    else -> null
}

const val DESTINO_FICHAR = "fichar"
const val DESTINO_AUSENCIAS = "ausencias"
const val DESTINO_AUSENCIAS_EQUIPO_PENDIENTES = "ausencias-equipo/pendientes"
const val DESTINO_AUSENCIAS_EQUIPO_RESUELTAS = "ausencias-equipo/resueltas"
