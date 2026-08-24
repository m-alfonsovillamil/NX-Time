package com.nxtime.nxtime.servicio.impl

import com.nxtime.nxtime.dominio.Empresa
import com.nxtime.nxtime.dominio.Rol
import com.nxtime.nxtime.dominio.Usuario
import com.nxtime.nxtime.dto.CambiarContrasenaRequest
import com.nxtime.nxtime.dto.CrearEmpleadoRequest
import com.nxtime.nxtime.dto.CrearGestorRequest
import com.nxtime.nxtime.dto.PeticionLogin
import com.nxtime.nxtime.dto.RegistroGestorRequest
import com.nxtime.nxtime.dto.RespuestaAutenticacion
import com.nxtime.nxtime.dto.EmpleadoSimpleDTO
import com.nxtime.nxtime.repositorio.EmpresaRepositorio
import com.nxtime.nxtime.repositorio.UsuarioRepositorio
import com.nxtime.nxtime.seguridad.JwtServicio
import com.nxtime.nxtime.servicio.ServicioAutenticacion
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.NoSuchElementException

/*
 * Implementación de la lógica de negocio para Usuarios y Seguridad.
 */
@Service
class ServicioAutenticacionImpl(
    private val usuarioRepositorio: UsuarioRepositorio,
    private val empresaRepositorio: EmpresaRepositorio,
    private val passwordEncoder: PasswordEncoder,
    private val jwtServicio: JwtServicio,
    private val authenticationManager: AuthenticationManager
) : ServicioAutenticacion {

    /*
     * Lógica para registrar un nuevo GESTOR
     */
    override fun registrarGestor(peticion: RegistroGestorRequest): RespuestaAutenticacion {

        if (empresaRepositorio.findByNombre(peticion.nombreEmpresa).isPresent) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "La empresa ya existe. Solicita acceso al administrador.")
        }

        val empresa = empresaRepositorio.save(Empresa(nombre = peticion.nombreEmpresa))

        val usuario = Usuario(
            nombre = peticion.nombreGestor,
            email = peticion.email,
            contrasena = passwordEncoder.encode(peticion.password),
            rol = Rol.GESTOR,
            empresa = empresa
        )

        val usuarioGuardado = usuarioRepositorio.save(usuario)
        val jwtToken = jwtServicio.generateToken(usuarioGuardado)

        return RespuestaAutenticacion(
            token = jwtToken,
            nombre = usuarioGuardado.nombre,
            rol = usuarioGuardado.rol
        )
    }

    /*
     * Inicio de sesión para cualquier usuario (Empleado o Gestor).
     */

    override fun login(peticion: PeticionLogin): RespuestaAutenticacion {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(
                peticion.email,
                peticion.contrasena
            )
        )

        val usuario = usuarioRepositorio.findByEmail(peticion.email)
            .orElseThrow { NoSuchElementException("Usuario no encontrado con email: ${peticion.email}") }

        val jwtToken = jwtServicio.generateToken(usuario)

        return RespuestaAutenticacion(
            token = jwtToken,
            nombre = usuario.nombre,
            rol = usuario.rol
        )
    }

    /*
     * Un Gestor crea un Empleado dentro de su misma empresa.
     */

    override fun crearEmpleado(peticion: CrearEmpleadoRequest, gestor: Usuario) {
        if (usuarioRepositorio.existsByEmail(peticion.email)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El email ya está registrado.")
        }

        val empresaDelGestor = gestor.empresa

        val nuevoEmpleado = Usuario(
            nombre = peticion.nombre,
            email = peticion.email,
            contrasena = passwordEncoder.encode(peticion.contrasena),
            rol = Rol.EMPLEADO,
            empresa = empresaDelGestor
        )

        usuarioRepositorio.save(nuevoEmpleado)
    }


    /**
     * Permite a un Gestor crear otro Gestor para su misma empresa.
     */
    override fun crearGestor(peticion: CrearGestorRequest, administrador: Usuario) {
        if (usuarioRepositorio.existsByEmail(peticion.email)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El email ya está registrado.")
        }


        val empresa = administrador.empresa

        val nuevoGestor = Usuario(
            nombre = peticion.nombre,
            email = peticion.email,
            contrasena = passwordEncoder.encode(peticion.contrasena),
            rol = Rol.GESTOR,
            empresa = empresa
        )

        usuarioRepositorio.save(nuevoGestor)
    }

        /*
         * Cambio de contraseña seguro.
         */

    override fun cambiarContrasena(peticion: CambiarContrasenaRequest, usuario: Usuario) {
        if (!passwordEncoder.matches(peticion.contrasenaAntigua, usuario.contrasena)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña antigua no es correcta.")
        }

        if (peticion.contrasenaNueva.length < 6) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 6 caracteres.")
        }

        usuario.contrasena = passwordEncoder.encode(peticion.contrasenaNueva)
        usuarioRepositorio.save(usuario)
    }

    /*
     * Devuelve lista simple de empleados de la empresa del gestor
     */

    override fun getMisEmpleados(gestor: Usuario): List<EmpleadoSimpleDTO> {
        val empresaDelGestor = gestor.empresa
        val empleados = usuarioRepositorio.findByEmpresaAndRol(empresaDelGestor, Rol.EMPLEADO)

        return empleados.map { empleado ->
            EmpleadoSimpleDTO(
                id = empleado.id,
                nombre = empleado.nombre,
                email = empleado.email
            )
        }
    }
}


