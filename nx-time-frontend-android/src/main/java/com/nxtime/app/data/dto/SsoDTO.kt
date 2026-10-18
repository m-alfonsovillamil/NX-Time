package com.nxtime.app.data.dto

/**
 * Un proveedor con el que se puede entrar en este servidor (ADR 036).
 *
 * @param inicio la URL adonde hay que mandar el navegador. Absoluta, y del
 * dominio público de la API, que **no es el que usa la app** para lo demás:
 * la cookie con la que el servidor reconoce la vuelta solo vuelve al mismo
 * nombre al que se fue.
 */
data class ProveedorSsoDTO(
    val id: String,
    val nombre: String,
    val inicio: String
)

/**
 * La app recoge su sesión con el código con el que volvió del navegador.
 *
 * @param verificador el valor aleatorio cuyo SHA-256 se mandó al empezar. Es
 * lo que demuestra que quien canjea es quien empezó: otra app que se quedara
 * con el código no lo tiene.
 */
data class PeticionCanjeSso(
    val codigo: String,
    val verificador: String
)

/**
 * Una cuenta de Google o de Microsoft con la que entro.
 *
 * @param proveedor el id (`google`, `microsoft`), que es con lo que se desvincula
 * @param correo el de esa cuenta cuando se vinculó; puede no ser el de NX Time
 */
data class IdentidadVinculadaDTO(
    val proveedor: String,
    val nombre: String,
    val correo: String? = null,
    val vinculadaEn: String? = null,
    val ultimoAcceso: String? = null
)

/**
 * Lo que devuelve el servidor al cambiar el código de registro (ADR 044): con
 * qué cuenta se está registrando la empresa y el pase para mandar el
 * formulario, que vale un cuarto de hora.
 */
data class RegistroPendienteSsoDTO(
    val proveedor: String,
    val nombre: String,
    val correo: String,
    val pase: String
)

/** Registrar la empresa con esa cuenta: ni correo ni contraseña, que es lo que ahorra. */
data class PeticionRegistroSso(
    val pase: String,
    val nombreEmpresa: String,
    val nombre: String,
    val apellidos: String,
    /** La casilla de las condiciones y el contrato de encargo (ADR 046). */
    val aceptaCondiciones: Boolean
)
