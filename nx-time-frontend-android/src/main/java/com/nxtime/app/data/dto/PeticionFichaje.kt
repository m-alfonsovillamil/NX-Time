package com.nxtime.app.data.dto

/**
 * Lo que se envía al backend para fichar.
 *
 * "tipo" era un String libre; ahora es {@link TipoFichaje}. Gson
 * serializa un enum por el nombre de su constante, así que el JSON que
 * viaja es exactamente el mismo ("INICIO", "PAUSA_FIN"...) y **el
 * contrato no cambia**: solo deja de poder escribirse mal.
 */
data class PeticionFichaje(
    val tipo: TipoFichaje
)
