package com.nxtime.nxtime.repositorio

import com.nxtime.nxtime.dominio.EstadoAusencia
import com.nxtime.nxtime.dominio.PeticionAusencia
import com.nxtime.nxtime.dominio.Usuario
import org.springframework.data.jpa.repository.JpaRepository

interface PeticionAusenciaRepositorio : JpaRepository<PeticionAusencia, Long> {

    fun findByUsuario(usuario: Usuario): List<PeticionAusencia>

    fun findByUsuario_Empresa_IdAndEstado(empresaId: Long, estado: EstadoAusencia): List<PeticionAusencia>


    /**
     * Busca todas las peticiones de una empresa que no tengan un estado específico
     */
    fun findByUsuario_Empresa_IdAndEstadoIsNot(empresaId: Long, estado: EstadoAusencia): List<PeticionAusencia>

}