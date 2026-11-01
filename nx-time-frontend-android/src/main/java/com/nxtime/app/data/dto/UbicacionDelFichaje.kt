package com.nxtime.app.data.dto

/** Presencial o a distancia (ADR 055). */
enum class Modalidad { PRESENCIAL, A_DISTANCIA }

/** Si se tomó la ubicación al fichar, o por qué no (ADR 056). */
enum class EstadoUbicacion { CAPTURADA, DENEGADA, NO_DISPONIBLE }

/**
 * Dónde se fichó (ADR 056): lo que se manda al entrar o al salir, y lo que
 * devuelve el historial. Sin coordenadas si no se pudo tomar, o si ya pasaron
 * los 12 meses y se borraron.
 *
 * @param precisionM el radio de confianza que da el móvil, en metros
 */
data class UbicacionDelFichaje(
    val estado: EstadoUbicacion,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val precisionM: Int? = null
)
