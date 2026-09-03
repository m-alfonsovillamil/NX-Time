package com.nxtime.app.data.repository

import com.nxtime.app.data.dto.*
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.network.ApiService
import com.nxtime.app.data.session.SessionManager
import retrofit2.Response

/*
 * Es el "intermediario" principal entre los ViewModels y la API
 */
class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : AuthRepository {

    /*  Implementación de Autenticación  */

    override suspend fun login(peticion: PeticionLogin): Response<RespuestaAutenticacion> {
        val response = apiService.login(peticion)
        if (response.isSuccessful && response.body() != null) {
            procesarLoginExitoso(response.body()!!)
        }
        return response
    }

    override suspend fun registrarEmpresaGestor(request: RegistroGestorRequest): Response<RespuestaAutenticacion> {
        return apiService.registrarEmpresaGestor(request)
    }

    override fun procesarLoginExitoso(authResponse: RespuestaAutenticacion) {
        sessionManager.saveAuthData(
            token = authResponse.token,
            refreshToken = authResponse.refreshToken,
            nombre = authResponse.nombre,
            rol = authResponse.rol
        )
    }

    /*  Implementación de Fichaje (Empleado) */

    override suspend fun getRegistroActivo(): Response<Registro?> {
        return apiService.getRegistroActivo()
    }

    override suspend fun registrarFichaje(peticion: PeticionFichaje): Response<Registro> {
        return apiService.registrarFichaje(peticion)
    }

    override suspend fun getHistorial(): Response<List<Registro>> {
        return apiService.getHistorial()
    }

    override suspend fun getResumenPersonal(): Response<ResumenPersonalDTO> {
        return apiService.getResumenPersonal()
    }

    override suspend fun corregirFichaje(
        fichajeId: Long,
        peticion: CorreccionFichajeRequest
    ): Response<Registro> {
        return apiService.corregirFichaje(fichajeId, peticion)
    }

    override suspend fun getAuditoriaFichaje(
        fichajeId: Long
    ): Response<List<AuditoriaFichajeDTO>> {
        return apiService.getAuditoriaFichaje(fichajeId)
    }

    /*  Implementación de Ausencias (Empleado)  */

    override suspend fun solicitarAusencia(peticion: PeticionAusenciaDTO): Response<RespuestaAusencia> {
        return apiService.solicitarAusencia(peticion)
    }

    override suspend fun getMisPeticiones(): Response<List<RespuestaAusencia>> {
        return apiService.getMisPeticiones()
    }

    /* Implementación de Ausencias (Gestor)  */

    override suspend fun getPeticionesPendientes(): Response<List<RespuestaAusencia>> {
        return apiService.getPeticionesPendientes()
    }

    // Fase 9 del backend: un único PATCH con el estado en el cuerpo,
    // en vez de dos POST distintos con la acción en la URL.
    override suspend fun cambiarEstadoPeticion(
        peticionId: Long,
        estado: EstadoAusencia,
        comentario: String?
    ): Response<RespuestaAusencia> {
        return apiService.cambiarEstadoPeticion(
            peticionId,
            CambioEstadoAusenciaRequest(estado, comentario)
        )
    }

    override suspend fun getSaldoVacaciones(): Response<SaldoVacacionesDTO> {
        return apiService.getSaldoVacaciones()
    }

    override suspend fun getHistorialAusencias(): Response<List<RespuestaAusencia>> {
        return apiService.getHistorialAusencias()
    }

    /*  Implementación de Historial (Gestor)  */

    override suspend fun getHistorialEquipo(): Response<List<RegistroEquipoDTO>> {
        return apiService.getHistorialEquipo()
    }

    /*  Implementación de Gestión (Gestor/Usuario)  */

    override suspend fun crearEmpleado(peticion: CrearEmpleadoRequest): Response<Unit> {
        return apiService.crearEmpleado(peticion)
    }

    override suspend fun cambiarContrasena(peticion: CambiarContrasenaRequest): Response<Unit> {
        return apiService.cambiarContrasena(peticion)
    }

    override suspend fun getMisEmpleados(): Response<List<EmpleadoSimpleDTO>> {
        return apiService.getMisEmpleados()
    }

    /*
     * Llama al endpoint para crear un nuevo Gestor.
     */
    override suspend fun crearGestor(peticion: CrearGestorRequest): Response<Unit> {
        return apiService.crearGestor(peticion)
    }

}