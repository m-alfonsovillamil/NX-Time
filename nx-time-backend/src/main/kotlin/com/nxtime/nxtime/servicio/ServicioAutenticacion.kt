package com.nxtime.nxtime.servicio

import com.nxtime.nxtime.dominio.Usuario
import com.nxtime.nxtime.dto.*
import com.nxtime.nxtime.dto.EmpleadoSimpleDTO

/*
 * Define las operaciones de negocio relacionadas con la autenticación y gestión de usuarios
 */
interface ServicioAutenticacion {

    /*
     * Define la función para registrar un nuevo Gestor (y su empresa).
     */
    fun registrarGestor(peticion: RegistroGestorRequest): RespuestaAutenticacion

    /*
     * Define la función para el login de cualquier usuario.
     */
    fun login(peticion: PeticionLogin): RespuestaAutenticacion

    /*
     * Define la función para que un Gestor cree un nuevo Empleado.
     */
    fun crearEmpleado(peticion: CrearEmpleadoRequest, gestor: Usuario)

    /*
     * Define la función para que un usuario logueado cambie su contraseña.
     */
    fun cambiarContrasena(peticion: CambiarContrasenaRequest, usuario: Usuario)

    /*
     * Define la función para que un Gestor obtenga la lista de sus empleados.
     */
    fun getMisEmpleados(gestor: Usuario): List<EmpleadoSimpleDTO>


    fun crearGestor(peticion: CrearGestorRequest, administrador: Usuario)
}