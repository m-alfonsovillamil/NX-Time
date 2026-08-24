package com.nxtime.nxtime.repositorio

import com.nxtime.nxtime.dominio.Empresa
import com.nxtime.nxtime.dominio.Rol
import com.nxtime.nxtime.dominio.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/*
 * Para poder buscar, guardar y borrar Usuarios
 */
interface UsuarioRepositorio : JpaRepository<Usuario, Long> {

    /*
     * Busca un Usuario por su columna 'email'. Devuelve un 'Optional' porque el usuario podría no existir.
     */
    fun findByEmail(email: String): Optional<Usuario>

    /*
     * Comprueba (true/false) si un usuario con ese 'email' ya existe. Es más eficiente que buscar el objeto entero.
     */
    fun existsByEmail(email: String): Boolean

    /*
     * Busca todos los usuarios que pertenecen a una 'Empresa' específica.
     */
    fun findByEmpresa(empresa: Empresa): List<Usuario>

    /*
     * Busca todos los usuarios de una 'Empresa' que tengan un 'Rol' específico.
     */
    fun findByEmpresaAndRol(empresa: Empresa, rol: Rol): List<Usuario>
}