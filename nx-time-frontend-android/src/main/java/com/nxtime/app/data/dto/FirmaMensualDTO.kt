package com.nxtime.app.data.dto

/**
 * Una firma del registro de un mes: `/api/v1/firmas` (Fase B3, ADR 025).
 *
 * Firma de aceptación, no firma electrónica cualificada: el servidor guarda
 * el SHA-256 de lo que se firmó (`hash`). Si después se corrige un fichaje del
 * mes, la firma pasa a `INVALIDADA` con su `motivoInvalidacion`, y el mes
 * vuelve a pedir firma.
 *
 * `estado` viaja como texto por lo mismo que [AvisoDTO.tipo].
 */
data class FirmaMensualDTO(
    val id: Long,
    val usuarioId: Long,
    val usuario: String,
    val anio: Int,
    val mes: Int,
    /** "VIGENTE" o "INVALIDADA". */
    val estado: String,
    val hash: String,
    val jornadas: Int,
    val segundosNetos: Long,
    val firmadaEn: String,
    val invalidadaEn: String? = null,
    val motivoInvalidacion: String? = null,
    val visadaPor: String? = null,
    val visadaEn: String? = null
)

/**
 * Un mes terminado, visto por quien lo firma.
 *
 * `bloqueo` llega ya redactado por el servidor ("Queda una jornada sin
 * cerrar..."): la regla de qué impide firmar vive allí, y repetirla aquí sería
 * tener dos verdades.
 */
data class MesParaFirmarDTO(
    val anio: Int,
    val mes: Int,
    val jornadas: Int,
    /** Lo trabajado en el mes, neto: lo que se acepta al firmar. */
    val segundosNetos: Long,
    val puedeFirmar: Boolean,
    val bloqueo: String? = null,
    /** La vigente o, si no la hay, la última que se invalidó. */
    val firma: FirmaMensualDTO? = null
)

data class FirmarMesRequest(val anio: Int, val mes: Int)
