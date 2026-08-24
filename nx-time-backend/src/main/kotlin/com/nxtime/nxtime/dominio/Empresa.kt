package com.nxtime.nxtime.dominio

import jakarta.persistence.*

/**
 * La tabla se llamará "empresas".
 */
@Entity(name = "empresas")
data class Empresa(

    @Id

    @TableGenerator(
        name = "empresa_gen",
        table = "id_generator",
        pkColumnName = "gen_name",
        valueColumnName = "gen_val",
        allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "empresa_gen")
    val id: Long = 0,


    val nombre: String
)