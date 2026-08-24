package com.nxtime.nxtime.servicio

import com.nxtime.nxtime.dominio.Registros
import com.nxtime.nxtime.dto.PeticionFichaje
import com.nxtime.nxtime.dto.RegistroEquipoDTO

/*
 * Define las operaciones de negocio relacionadas con el Fichaje.
 */
interface ServicioFichaje {

    /*
     * Define la función para registrar un fichaje
     */
    fun registrarFichaje(emailUsuario: String, peticion: PeticionFichaje): Registros

    /*
     * Define la función para ver el fichaje activo (abierto) de un usuario.
     */
    fun getRegistroActivo(emailUsuario: String): Registros?

    /*
     * Define la función para que un empleado vea su propio historial.
     */
    fun getHistorial(emailUsuario: String): List<Registros>

    /*
     * Define la función para que un Gestor vea el historial de todo su equipo.
     */
    fun getHistorialEquipo(emailGestor: String): List<RegistroEquipoDTO>
}