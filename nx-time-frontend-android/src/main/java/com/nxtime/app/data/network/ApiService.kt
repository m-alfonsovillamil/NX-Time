package com.nxtime.app.data.network

import com.nxtime.app.data.dto.*
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.CrearGestorRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/*
 * Cada función aquí es un endpoint de la API.
 */
interface ApiService {

    /*  Autenticación (Rutas Públicas)  */

    @POST("auth/login")
    suspend fun login(
        @Body peticion: PeticionLogin
    ): Response<RespuestaAutenticacion>

    @POST("auth/register-manager")
    suspend fun registrarEmpresaGestor(
        @Body peticion: RegistroGestorRequest
    ): Response<RespuestaAutenticacion>

    @POST("auth/refresh")
    suspend fun refrescarToken(
        @Body peticion: RefreshTokenRequest
    ): Response<RespuestaAutenticacion>

    @POST("auth/logout")
    suspend fun cerrarSesionRemota(
        @Body peticion: RefreshTokenRequest
    ): Response<Unit>


    /*  Endpoints de Fichaje (Empleado)  */

    @GET("api/v1/fichaje/activo")
    suspend fun getRegistroActivo(): Response<Registro?>

    @POST("api/v1/fichaje")
    suspend fun registrarFichaje(
        @Body peticion: PeticionFichaje
    ): Response<Registro>

    @GET("api/v1/fichaje/historial")
    suspend fun getHistorial(): Response<List<Registro>>

    /*  Resumen personal (cualquier usuario con `fichaje:leer`)  */

    @GET("api/v1/dashboard/resumen")
    suspend fun getResumenPersonal(): Response<ResumenPersonalDTO>

    /*
     *  Cumplimiento normativo (RRHH/ADMIN)
     *
     *  Corregir nunca sobrescribe: el backend anula el fichaje original
     *  y crea uno nuevo enlazado, y devuelve EL NUEVO. Toda la operación
     *  queda en la traza de auditoría.
     */

    @PATCH("api/v1/fichaje/{id}")
    suspend fun corregirFichaje(
        @Path("id") fichajeId: Long,
        @Body peticion: CorreccionFichajeRequest
    ): Response<Registro>

    @GET("api/v1/auditoria/fichaje/{id}")
    suspend fun getAuditoriaFichaje(
        @Path("id") fichajeId: Long
    ): Response<List<AuditoriaFichajeDTO>>

    /*  Panel de empresa y gestión de altas/bajas  */

    @GET("api/v1/dashboard/empresa")
    suspend fun getPanelEmpresa(): Response<PanelEmpresaDTO>

    @PATCH("api/v1/gestor/empleados/{id}/estado")
    suspend fun cambiarEstadoEmpleado(
        @Path("id") empleadoId: Long,
        @Body cambio: CambioEstadoEmpleadoRequest
    ): Response<Unit>

    /*
     *  Informes (RRHH/ADMIN).
     *
     *  @Streaming es obligatorio: sin él, Retrofit carga el fichero
     *  entero en memoria antes de devolverlo. Un Excel de la empresa
     *  puede ser grande y no hay razón para tenerlo dos veces.
     */

    @Streaming
    @GET("api/v1/informes/horas")
    suspend fun descargarExcelDeHoras(
        @Query("anio") anio: Int,
        @Query("mes") mes: Int
    ): Response<ResponseBody>

    @Streaming
    @GET("api/v1/informes/mensual/{empleadoId}")
    suspend fun descargarPdfMensual(
        @Path("empleadoId") empleadoId: Long,
        @Query("anio") anio: Int,
        @Query("mes") mes: Int
    ): Response<ResponseBody>

    /*  Endpoints de Ausencias (Empleado)  */

    @POST("api/v1/ausencias")
    suspend fun solicitarAusencia(
        @Body peticion: PeticionAusenciaDTO
    ): Response<RespuestaAusencia>

    @GET("api/v1/ausencias/mis-peticiones")
    suspend fun getMisPeticiones(): Response<List<RespuestaAusencia>>

    /*  Endpoints de GESTOR (Ausencias)  */

    @GET("api/v1/ausencias/gestor/pendientes")
    suspend fun getPeticionesPendientes(): Response<List<RespuestaAusencia>>

    /*
     * Fase 9 del backend: un único PATCH sustituye a los dos POST
     * anteriores (.../gestor/aprobar/{id} y .../gestor/rechazar/{id}).
     * Cambiar el estado de un recurso que ya existe es un PATCH.
     */
    @PATCH("api/v1/ausencias/{id}/estado")
    suspend fun cambiarEstadoPeticion(
        @Path("id") peticionId: Long,
        @Body cambio: CambioEstadoAusenciaRequest
    ): Response<RespuestaAusencia>

    @GET("api/v1/ausencias/saldo-vacaciones")
    suspend fun getSaldoVacaciones(): Response<SaldoVacacionesDTO>

    /*  Endpoints de GESTOR (Varios) */

    @GET("api/v1/fichaje/gestor/historial")
    suspend fun getHistorialEquipo(): Response<List<RegistroEquipoDTO>>

    @POST("api/v1/gestor/empleados")
    suspend fun crearEmpleado(
        @Body peticion: CrearEmpleadoRequest
    ): Response<Unit>

    @GET("api/v1/gestor/mis-empleados")
    suspend fun getMisEmpleados(): Response<List<EmpleadoSimpleDTO>>

    @GET("api/v1/gestor/ausencias-historial")
    suspend fun getHistorialAusencias(): Response<List<RespuestaAusencia>>

    /**
     * Configura la jornada semanal y/o los días de vacaciones del año
     * en curso. Es un PATCH: lo que va a null no se toca.
     */
    @PATCH("api/v1/gestor/empleados/{id}/ficha")
    suspend fun configurarFichaEmpleado(
        @Path("id") empleadoId: Long,
        @Body ficha: FichaEmpleadoRequest
    ): Response<EmpleadoSimpleDTO>


    /* Endpoint CREAR GESTOR */

    @POST("api/v1/gestor/gestores")
    suspend fun crearGestor(
        @Body peticion: CrearGestorRequest
    ): Response<Unit>



    /*  Endpoint de USUARIO (Empleado o Gestor) */

    @POST("api/v1/usuario/cambiar-contrasena")
    suspend fun cambiarContrasena(
        @Body peticion: CambiarContrasenaRequest
    ): Response<Unit>


    /*  Endpoints de PERFIL (Fase B) */

    @GET("api/v1/perfil")
    suspend fun getMiPerfil(): Response<PerfilDTO>

    @PATCH("api/v1/perfil")
    suspend fun actualizarMiPerfil(@Body cambios: ActualizarPerfilRequest): Response<PerfilDTO>

    @GET("api/v1/departamentos")
    suspend fun getDepartamentos(): Response<List<DepartamentoDTO>>

    @POST("api/v1/departamentos")
    suspend fun crearDepartamento(@Body peticion: DepartamentoRequest): Response<DepartamentoDTO>

    @DELETE("api/v1/departamentos/{id}")
    suspend fun borrarDepartamento(@Path("id") id: Long): Response<Unit>

    /** `departamentoId` a null saca al empleado del que tuviera. */
    @PATCH("api/v1/departamentos/empleados/{usuarioId}")
    suspend fun asignarDepartamento(
        @Path("usuarioId") usuarioId: Long,
        @Body peticion: AsignarDepartamentoRequest
    ): Response<PerfilDTO>


    /*  Endpoints de AVISOS (cualquiera con sesión iniciada) */

    @GET("api/v1/avisos")
    suspend fun getAvisos(): Response<List<AvisoDTO>>

    /** Solo el contador: se pide mucho más a menudo que la lista. */
    @GET("api/v1/avisos/no-leidos")
    suspend fun getContadorAvisos(): Response<ContadorAvisosDTO>

    @PATCH("api/v1/avisos/{id}/leido")
    suspend fun marcarAvisoLeido(@Path("id") avisoId: Long): Response<Unit>

    @PATCH("api/v1/avisos/leer-todos")
    suspend fun marcarTodosLosAvisosLeidos(): Response<Unit>

}