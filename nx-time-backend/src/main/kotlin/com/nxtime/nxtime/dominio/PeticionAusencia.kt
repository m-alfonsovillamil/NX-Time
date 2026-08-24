package com.nxtime.nxtime.dominio

import jakarta.persistence.*
import java.time.LocalDate

/*
 * llamada "peticiones_ausencia".
 */

@Entity(name = "peticiones_ausencia")
data class PeticionAusencia(

    @Id
    /*
     * Usa la tabla "id_generator" para crear IDs únicos.
     */

    @TableGenerator(
        name = "peticion_gen",
        table = "id_generator",
        pkColumnName = "gen_name",
        valueColumnName = "gen_val",
        allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "peticion_gen")
    val id: Long = 0,

    /*
     * Muchas peticiones pertenecen a Un usuario.
     */

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    val usuario: Usuario,

    val fechaInicio: LocalDate,
    val fechaFin: LocalDate,

    /*
     * Por ejemplo: "VACACIONES", en lugar de un número.
     */

    @Enumerated(EnumType.STRING)
    val tipo: TipoAusencia,

    @Enumerated(EnumType.STRING)
    var estado: EstadoAusencia = EstadoAusencia.PENDIENTE,

    val motivo: String? = null
)