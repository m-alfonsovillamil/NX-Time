package com.nxtime.nxtime.repositorio

import com.nxtime.nxtime.dominio.Registros
import com.nxtime.nxtime.dominio.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/*
 * Para poder buscar, guardar y borrar Registros.
 */
interface RegistroRepositorio : JpaRepository<Registros, Long> {

    /*
     * Busca un Registro que pertenezca a un Usuario específico y donde la columna 'horaSalida' sea nula devuelve un 'Optional' porque podría no existir.
     */
    fun findByUsuarioAndHoraSalidaIsNull(usuario: Usuario): Optional<Registros>

    /*
     * Busca todos los registros de un 'Usuario' específico y los ordena por 'horaEntrada' de forma descendente
     */
    fun findByUsuarioOrderByHoraEntradaDesc(usuario: Usuario): List<Registros>

    /*
     * Busca todos los registros que pertenezcan a cualquier usuario dentro de la 'lista de usuarios' que le pasamos. Los ordena por 'horaEntrada' de forma descendente.
     */
    fun findByUsuarioInOrderByHoraEntradaDesc(usuarios: List<Usuario>): List<Registros>
}