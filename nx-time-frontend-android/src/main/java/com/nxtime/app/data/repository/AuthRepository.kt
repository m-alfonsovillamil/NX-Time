package com.nxtime.app.data.repository

import com.nxtime.app.data.dto.*
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import okhttp3.ResponseBody
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
    suspend fun getResumenPersonal(): Response<ResumenPersonalDTO>

    /* Cumplimiento normativo (RRHH/ADMIN) */
    suspend fun corregirFichaje(
        fichajeId: Long,
        peticion: CorreccionFichajeRequest
    ): Response<Registro>

    suspend fun getAuditoriaFichaje(fichajeId: Long): Response<List<AuditoriaFichajeDTO>>

    /* Panel de empresa, altas/bajas e informes */
    suspend fun getPanelEmpresa(): Response<PanelEmpresaDTO>

    suspend fun cambiarEstadoEmpleado(empleadoId: Long, activo: Boolean): Response<Unit>

    suspend fun descargarExcelDeHoras(anio: Int, mes: Int): Response<ResponseBody>

    suspend fun descargarPdfMensual(
        empleadoId: Long,
        anio: Int,
        mes: Int
    ): Response<ResponseBody>

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

    /**
     * Configura la ficha de un empleado. Los parámetros a null no se
     * tocan en el servidor.
     */
    suspend fun configurarFichaEmpleado(
        empleadoId: Long,
        horasSemanales: String?,
        diasVacaciones: Int?
    ): Response<EmpleadoSimpleDTO>

    /* Funciones de Perfil (Fase B) */
    suspend fun getMiPerfil(): Response<PerfilDTO>
    suspend fun actualizarMiPerfil(
        nombre: String?,
        apellidos: String?,
        fechaNacimiento: String?,
        puesto: String?
    ): Response<PerfilDTO>
    suspend fun getDepartamentos(): Response<List<DepartamentoDTO>>
    suspend fun crearDepartamento(nombre: String): Response<DepartamentoDTO>
    suspend fun borrarDepartamento(id: Long): Response<Unit>
    suspend fun asignarDepartamento(usuarioId: Long, departamentoId: Long?): Response<PerfilDTO>

    /* Funciones de Avisos */
    suspend fun getAvisos(): Response<List<AvisoDTO>>
    suspend fun getContadorAvisos(): Response<ContadorAvisosDTO>
    suspend fun marcarAvisoLeido(avisoId: Long): Response<Unit>
    suspend fun marcarTodosLosAvisosLeidos(): Response<Unit>

}