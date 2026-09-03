package com.nxtime.app.data.network

import com.nxtime.app.data.dto.*
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.CrearGestorRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

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

}