package com.nxtime.app.ui.auditoria

import com.google.gson.JsonParser

/**
 * Los dos campos que la pantalla saca de las instantáneas de auditoría.
 */
data class InstantaneaFichaje(
    val horaEntrada: String?,
    val horaSalida: String?
)

/**
 * Lee `valorAnterior` / `valorNuevo`, que llegan como **JSON crudo en una
 * cadena**.
 *
 * Se parsea a mano y no con un DTO tipado a propósito: la instantánea la
 * escribe `TimeEntrySnapshotSerializer` con la forma que tuviera el
 * fichaje **en el momento del cambio**, y esos registros son inmutables
 * (hay un trigger en la base que impide UPDATE y DELETE). Si la forma
 * cambiara con los años, un DTO tipado reventaría al leer los registros
 * viejos -- y una auditoría que no se puede releer no sirve para nada.
 * Aquí, un campo que falte simplemente sale como `null`.
 *
 * Tampoco se vuelca el JSON en pantalla: `{"id":42,"enPausa":false,...}`
 * no es información para nadie que esté revisando un registro horario.
 */
fun leerInstantanea(json: String?): InstantaneaFichaje? {
    if (json.isNullOrBlank()) return null
    return try {
        val objeto = JsonParser.parseString(json).asJsonObject
        InstantaneaFichaje(
            horaEntrada = objeto.textoDe("horaEntrada"),
            horaSalida = objeto.textoDe("horaSalida")
        )
    } catch (e: Exception) {
        // Un registro viejo o corrupto no debe tirar la pantalla abajo:
        // el resto de la línea temporal sigue siendo válido y útil.
        null
    }
}

private fun com.google.gson.JsonObject.textoDe(campo: String): String? =
    get(campo)?.takeIf { !it.isJsonNull }?.asString
