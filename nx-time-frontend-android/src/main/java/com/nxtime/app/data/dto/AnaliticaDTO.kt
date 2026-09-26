package com.nxtime.app.data.dto

/**
 * Las cifras de un vistazo de la analítica (`GET /api/v1/analitica/resumen`,
 * Fase B4 del backend).
 *
 * La app solo usa esto: una tarjeta con dos porcentajes en el panel de
 * empresa. El desglose por departamento o por persona es una tabla que no
 * cabe en 400 dp, y vive en la web (ADR 026).
 *
 * Los porcentajes llegan con un decimal y pueden ser **null**: el día 1 del
 * mes todavía no ha terminado ningún día, y un 0 % diría que nadie ha faltado.
 */
data class ResumenAnaliticaDTO(
    val ventana: VentanaAnaliticaDTO,
    val personas: Int = 0,
    val absentismo: Double? = null,
    val absentismoSinJustificar: Double? = null,
    val puntualidad: Double? = null,
    val retrasoMedioMinutos: Double? = null,
    val jornadasIncompletas: Double? = null,
    val minutosMediosPorDia: Long? = null
)

/**
 * Sobre qué se calculó: el periodo, hasta qué día se ha contado y qué parte
 * de la empresa (`EMPRESA` o `DEPARTAMENTO`, con su nombre).
 */
data class VentanaAnaliticaDTO(
    val periodo: String,
    val desde: String,
    val hasta: String,
    val evaluadoHasta: String? = null,
    val alcance: String,
    val departamento: String? = null
)
