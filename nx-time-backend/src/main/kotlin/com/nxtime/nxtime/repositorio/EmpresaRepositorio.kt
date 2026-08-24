package com.nxtime.nxtime.repositorio

import com.nxtime.nxtime.dominio.Empresa
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

// Esta interfaz nos da todos los métodos básicos (guardar, buscar, borrar)

interface EmpresaRepositorio : JpaRepository<Empresa, Long> {


    fun findByNombre(nombre: String): Optional<Empresa>
}