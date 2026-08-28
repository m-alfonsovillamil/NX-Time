package com.nxtime.app.data.repository

import com.nxtime.app.data.dto.*
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import retrofit2.Response

/*
 * Define las funciones que la app puede usar para interactuar con el backend (API) y la sesión local.
 */
interface AuthRepository {

    /* Funciones de Autenticación */
    suspend fun login(peticion: PeticionLogin): Response<RespuestaAutenticacion>
    suspend fun registrarEmpresaGestor(request: RegistroGestorRequest): Response<RespuestaAutenticacion>
    fun procesarLoginExitoso(authResponse: RespuestaAutenticacion)

    /* Funciones de Fichaje (Empleado) */
    suspend fun getRegistroActivo(): Response<Registro?>
    suspend fun registrarFichaje(peticion: PeticionFichaje): Response<Registro>
    suspend fun getHistorial(): Response<List<Registro>>

    /* Funciones de Ausencias (Empleado) */
    suspend fun solicitarAusencia(peticion: PeticionAusenciaDTO): Response<RespuestaAusencia>
    suspend fun getMisPeticiones(): Response<List<RespuestaAusencia>>

    /* Funciones de Ausencias (Gestor) */
    suspend fun getPeticionesPendientes(): Response<List<RespuestaAusencia>>
    suspend fun cambiarEstadoPeticion(
        peticionId: Long,
        estado: EstadoAusencia,
        comentario: String? = null
    ): Response<RespuestaAusencia>

    suspend fun getSaldoVacaciones(): Response<SaldoVacacionesDTO>
    suspend fun getHistorialAusencias(): Response<List<RespuestaAusencia>>

    /* Funciones de Historial (Gestor) */
    suspend fun getHistorialEquipo(): Response<List<RegistroEquipoDTO>>
    suspend fun getMisEmpleados(): Response<List<EmpleadoSimpleDTO>>

    /* Funciones de Gestión (Gestor/Usuario) */
    suspend fun crearEmpleado(peticion: CrearEmpleadoRequest): Response<Unit>
    suspend fun cambiarContrasena(peticion: CambiarContrasenaRequest): Response<Unit>


    /**
     * Llama al ApiService para crear un nuevo co-gestor.
     */
    suspend fun crearGestor(peticion: CrearGestorRequest): Response<Unit>

}