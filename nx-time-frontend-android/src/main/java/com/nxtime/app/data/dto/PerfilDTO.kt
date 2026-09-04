package com.nxtime.app.data.dto

/**
 * La ficha de una persona: `GET /api/v1/perfil`.
 *
 * `nombreCompleto` e `iniciales` los calcula el servidor y no la app a
 * propósito: la regla de las iniciales tiene un caso raro (sin
 * apellidos son las DOS primeras letras del nombre, no una letra suelta
 * en el círculo) que si vive en cada cliente se implementa distinto en
 * cada sitio.
 *
 * `fechaNacimiento` viaja como `yyyy-MM-dd`, no como instante: es una
 * fecha de calendario y no un momento, así que no lleva zona.
 */
data class PerfilDTO(
    val id: Long,
    val email: String,
    val nombre: String,
    val apellidos: String? = null,
    val nombreCompleto: String,
    val iniciales: String,
    val fechaNacimiento: String? = null,
    val puesto: String? = null,
    val departamentoId: Long? = null,
    val departamentoNombre: String? = null,
    val rol: String,
    val activo: Boolean = true,
    val horasSemanales: String = "40.0",
    val diasVacaciones: Int = 22
)

/**
 * Cuerpo de `PATCH /api/v1/perfil`.
 *
 * Solo datos personales: ni rol, ni jornada, ni vacaciones, ni
 * departamento. No es que la app no los mande — es que el backend no
 * los acepta por aquí, y eso es lo que impide que alguien se ascienda
 * editando su propio perfil.
 *
 * Null = no tocar. Cadena vacía borra el dato.
 */
data class ActualizarPerfilRequest(
    val nombre: String? = null,
    val apellidos: String? = null,
    val fechaNacimiento: String? = null,
    val puesto: String? = null
)

/** Un departamento y cuánta gente tiene dentro. */
data class DepartamentoDTO(
    val id: Long,
    val nombre: String,
    val empleados: Long = 0
)

/** Alta de un departamento. */
data class DepartamentoRequest(val nombre: String)

/**
 * Cuerpo de `PATCH /api/v1/departamentos/empleados/{id}`.
 *
 * Aquí null **sí** significa algo — sacar al empleado del departamento
 * que tuviera —, al revés que en el resto de los PATCH.
 */
data class AsignarDepartamentoRequest(val departamentoId: Long?)
