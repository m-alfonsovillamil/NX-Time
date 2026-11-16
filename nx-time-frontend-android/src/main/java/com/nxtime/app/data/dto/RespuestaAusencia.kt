package com.nxtime.app.data.dto

/**
 * DTO que la app Android RECIBIRÁ del backend al consultar el historial.
 * Se ha añadido la propiedad 'usuario' para que el GestorAusenciasAdapter pueda acceder al nombre del empleado.
 *
 * Desde la Fase 9 del backend llegan además la trazabilidad de la
 * resolución (aprobadoPor / fechaResolucion / comentarioResolucion, los
 * tres nulos mientras siga PENDIENTE) y 'diasHabiles', los días que
 * realmente consume la ausencia sin contar fines de semana ni festivos.
 * Todos son opcionales en Kotlin para no romper si el backend aún no los
 * envía.
 */
data class RespuestaAusencia(
    val id: Long,
    val fechaInicio: String,
    val fechaFin: String,
    val tipo: TipoAusencia,
    val estado: EstadoAusencia,
    val motivo: String?,

    val usuario: UsuarioSimpleDTO,

    val aprobadoPor: UsuarioSimpleDTO? = null,
    val fechaResolucion: String? = null,
    val comentarioResolucion: String? = null,
    // Con decimales desde el ADR 063: un medio día son 0,5.
    val diasHabiles: Double = 0.0,
    val medioDia: Boolean = false,
    // ADR 058: quién la canceló y por qué, y si quien pregunta puede
    // cancelarla ahora (lo decide el servidor, la app no repite la regla).
    val canceladaPor: UsuarioSimpleDTO? = null,
    val canceladaEn: String? = null,
    val motivoCancelacion: String? = null,
    val puedoCancelar: Boolean = false
)
