package com.nxtime.nxtime.dominio

import jakarta.persistence.*
import java.time.LocalDateTime

/*
 * llamada "registros".
 */
@Entity(name = "registros")
data class Registros(

    @Id
    /*
     * Usa la tabla "id_generator" para crear IDs únicos.
     */

    @TableGenerator(
        name = "registros_gen",
        table = "id_generator",
        pkColumnName = "gen_name",
        valueColumnName = "gen_val",
        allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "registros_gen")
    val id: Long = 0,

    val horaEntrada: LocalDateTime,

    var horaSalida: LocalDateTime? = null,

    var pausas: String? = null,

    var enPausa: Boolean = false,

    /*
     * Se limpia al reanudar.
     */
    var inicioPausaActual: LocalDateTime? = null,

    /*
     * Acumula el total de minutos que este fichaje ha estado en pausa.
     */
    var minutosPausaAcumulados: Long = 0,

    /*
     * Muchos registros pertenecen a Un usuario.
     */

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    val usuario: Usuario
)