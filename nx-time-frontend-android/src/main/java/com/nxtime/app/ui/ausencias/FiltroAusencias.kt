package com.nxtime.app.ui.ausencias

import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.dto.TipoAusencia

/**
 * Lo que el empleado ha elegido para acotar su lista de ausencias.
 * `null` en un campo = sin filtrar por él.
 *
 * Se filtra en la app y no en el servidor: las peticiones propias son unas
 * decenas al año y ya llegan todas en `GET /ausencias/mis-peticiones`.
 */
data class FiltroAusencias(
    val estado: EstadoAusencia? = null,
    val anio: Int? = null,
    val tipo: TipoAusencia? = null
) {
    val activo: Boolean get() = estado != null || anio != null || tipo != null

    /**
     * Una ausencia es de un año si **empieza o acaba** en él: unas vacaciones
     * del 28 de diciembre al 3 de enero salen al filtrar por cualquiera de
     * los dos. Dejarla fuera de uno sería perderla justo al buscarla.
     */
    fun deja(peticion: RespuestaAusencia): Boolean =
        (estado == null || peticion.estado == estado) &&
            (tipo == null || peticion.tipo == tipo) &&
            (anio == null || anioDe(peticion.fechaInicio) == anio || anioDe(peticion.fechaFin) == anio)

    companion object {
        fun filtrar(peticiones: List<RespuestaAusencia>, filtro: FiltroAusencias) =
            peticiones.filter(filtro::deja)

        /** Los años que aparecen en la lista, del más reciente al más antiguo. */
        fun aniosDe(peticiones: List<RespuestaAusencia>): List<Int> =
            peticiones.flatMap { listOfNotNull(anioDe(it.fechaInicio), anioDe(it.fechaFin)) }
                .distinct()
                .sortedDescending()

        /** Solo los tipos que se han pedido alguna vez: ofrecer los once sería ruido. */
        fun tiposDe(peticiones: List<RespuestaAusencia>): List<TipoAusencia> =
            peticiones.map { it.tipo }.distinct().sortedBy { it.ordinal }

        /** Las fechas llegan como "YYYY-MM-DD"; basta con el prefijo. */
        private fun anioDe(fechaIso: String): Int? = fechaIso.take(4).toIntOrNull()
    }
}
