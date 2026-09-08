package com.nxtime.app.data.network

import com.nxtime.app.data.dto.*
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.CrearGestorRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Multipart
import retrofit2.http.Part
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

    /**
     * Pide corregir un fichaje. **No lo corrige**: crea una solicitud.
     *
     * Sustituye al PATCH que aplicaba el cambio en el acto. La respuesta
     * llega con 202 si queda pendiente y 200 si se ha auto-aprobado; el
     * campo `estado` dice cual de los dos ha sido, asi que la app no
     * necesita mirar el codigo HTTP.
     */
    @POST("api/v1/fichaje/{id}/correcciones")
    suspend fun solicitarCorreccion(
        @Path("id") fichajeId: Long,
        @Body peticion: CorreccionFichajeRequest
    ): Response<CorreccionDTO>

    @GET("api/v1/correcciones/pendientes")
    suspend fun getCorreccionesPendientes(): Response<List<CorreccionDTO>>

    @GET("api/v1/correcciones/mias")
    suspend fun getMisCorrecciones(): Response<List<CorreccionDTO>>

    @PATCH("api/v1/correcciones/{id}/estado")
    suspend fun resolverCorreccion(
        @Path("id") correccionId: Long,
        @Body peticion: ResolverCorreccionRequest
    ): Response<CorreccionDTO>

    @POST("api/v1/correcciones/{id}/disputa")
    suspend fun disputarCorreccion(
        @Path("id") correccionId: Long,
        @Body peticion: DisputaRequest
    ): Response<CorreccionDTO>

    /*  Horas extra (Fase F)  */

    /**
     * Mis avisos de exceso de jornada. No pide permisos de gestion: son
     * mis horas, y el aviso me llega a mi antes que a nadie.
     */
    @GET("api/v1/horas-extra")
    suspend fun getMisHorasExtra(
        @Query("anio") anio: Int? = null
    ): Response<List<HorasExtraDTO>>

    /** La bandeja de quien revisa. Los ABIERTO salen primero. */
    @GET("api/v1/horas-extra/equipo")
    suspend fun getHorasExtraDelEquipo(
        @Query("anio") anio: Int? = null
    ): Response<List<HorasExtraDTO>>

    /**
     * Aceptar el exceso (cuenta para la bolsa) o justificarlo (se
     * archiva). Nadie revisa los suyos propios: eso lo rechaza el
     * servidor con 403 aunque el rol tenga la authority.
     */
    @PATCH("api/v1/horas-extra/{id}")
    suspend fun revisarHorasExtra(
        @Path("id") avisoId: Long,
        @Body peticion: RevisarHorasExtraRequest
    ): Response<HorasExtraDTO>

    /**
     * La bolsa anual del art. 35.2 ET. Sin `usuarioId` devuelve la
     * propia; con el, la de otra persona, y eso ya pide permisos.
     */
    @GET("api/v1/horas-extra/bolsa")
    suspend fun getBolsaHorasExtra(
        @Query("usuarioId") usuarioId: Long? = null,
        @Query("anio") anio: Int? = null
    ): Response<BolsaHorasExtraDTO>

    /*  Canal de denuncias (Fase G)  */

    /**
     * Presentar una denuncia. Devuelve el codigo de seguimiento UNA sola
     * vez: el servidor guarda solo su hash y no hay endpoint que lo
     * reenvie, porque poder recuperarlo seria poder demostrar que una
     * denuncia anonima es tuya.
     */
    @POST("api/v1/denuncias")
    suspend fun presentarDenuncia(
        @Body peticion: CrearDenunciaRequest
    ): Response<DenunciaCreadaDTO>

    /**
     * Seguir una denuncia con su codigo. Va autenticado (el canal es
     * interno) pero el servidor NO comprueba quien lo trae: el codigo es
     * la credencial, y comprobar la identidad seria negar el anonimato.
     */
    @GET("api/v1/denuncias/seguimiento/{codigo}")
    suspend fun getDenunciaPorCodigo(
        @Path("codigo") codigo: String
    ): Response<DenunciaDTO>

    /** Contestar como denunciante, con el codigo. */
    @POST("api/v1/denuncias/seguimiento/{codigo}/mensajes")
    suspend fun responderDenunciaPorCodigo(
        @Path("codigo") codigo: String,
        @Body peticion: MensajeDenunciaRequest
    ): Response<DenunciaDTO>

    /**
     * Las que presente identificandome. Las anonimas NO salen aqui y no
     * pueden salir: no hay ningun dato que las relacione conmigo.
     */
    @GET("api/v1/denuncias/mias")
    suspend fun getMisDenuncias(): Response<List<ResumenDenunciaDTO>>

    /**
     * Un expediente propio, sin el codigo. Solo vale para las que
     * presente IDENTIFICANDOME: sobre una anonima da 404 aunque sea mia,
     * porque el sistema no sabe que lo es.
     */
    @GET("api/v1/denuncias/mias/{id}")
    suspend fun getMiDenuncia(@Path("id") denunciaId: Long): Response<DenunciaDTO>

    /** Responder en un expediente propio identificado. */
    @POST("api/v1/denuncias/mias/{id}/mensajes")
    suspend fun responderMiDenuncia(
        @Path("id") denunciaId: Long,
        @Body peticion: MensajeDenunciaRequest
    ): Response<DenunciaDTO>

    /** La bandeja de quien instruye. Solo ADMIN (`denuncia:instruir`). */
    @GET("api/v1/denuncias")
    suspend fun getBandejaDenuncias(): Response<List<ResumenDenunciaDTO>>

    /** Un expediente por id. Solo ADMIN. */
    @GET("api/v1/denuncias/{id}")
    suspend fun getDenuncia(@Path("id") denunciaId: Long): Response<DenunciaDTO>

    /** Contestar como instructor. Vale como acuse de recibo si no lo habia. */
    @POST("api/v1/denuncias/{id}/mensajes")
    suspend fun responderDenunciaComoInstructor(
        @Path("id") denunciaId: Long,
        @Body peticion: MensajeDenunciaRequest
    ): Response<DenunciaDTO>

    /** Mover de estado. Cerrarla exige conclusion. */
    @PATCH("api/v1/denuncias/{id}/estado")
    suspend fun cambiarEstadoDenuncia(
        @Path("id") denunciaId: Long,
        @Body peticion: CambiarEstadoDenunciaRequest
    ): Response<DenunciaDTO>

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


    /*  Endpoints de ADJUNTOS: CV y foto (Fase B2) */

    @GET("api/v1/perfil/adjuntos")
    suspend fun getMisAdjuntos(): Response<List<AdjuntoDTO>>

    @Multipart
    @POST("api/v1/perfil/adjuntos")
    suspend fun subirAdjunto(
        @Part fichero: MultipartBody.Part,
        @Part("tipo") tipo: RequestBody
    ): Response<AdjuntoDTO>

    /**
     * Los bytes de un adjunto.
     *
     * `@Streaming` para no cargar el cuerpo entero en memoria antes de
     * escribirlo, igual que los informes. Y por el mismo motivo,
     * RetrofitClient tiene que dejar esta ruta fuera del interceptor de
     * logging: a nivel BODY bufferiza la respuesta y la rompe.
     */
    @Streaming
    @GET("api/v1/perfil/adjuntos/{id}")
    suspend fun descargarAdjunto(@Path("id") adjuntoId: Long): Response<ResponseBody>

    @DELETE("api/v1/perfil/adjuntos/{id}")
    suspend fun borrarAdjunto(@Path("id") adjuntoId: Long): Response<Unit>

    /*  Endpoints de CALENDARIO (Fase C) */

    /**
     * Un mes de calendario. Sin `anio`/`mes` devuelve el actual.
     *
     * `equipo` se atiende solo si el rol lo permite; sin permiso la
     * respuesta llega igual, con las ausencias propias y con
     * `incluyeEquipo` a false. No es un 403.
     */
    @GET("api/v1/calendario")
    suspend fun getCalendario(
        @Query("anio") anio: Int,
        @Query("mes") mes: Int,
        @Query("equipo") equipo: Boolean
    ): Response<CalendarioDTO>

    @POST("api/v1/calendario/festivos")
    suspend fun crearFestivo(@Body peticion: FestivoRequest): Response<FestivoDTO>

    @PATCH("api/v1/calendario/festivos/{id}")
    suspend fun editarFestivo(
        @Path("id") id: Long,
        @Body peticion: FestivoRequest
    ): Response<FestivoDTO>

    @DELETE("api/v1/calendario/festivos/{id}")
    suspend fun borrarFestivo(@Path("id") id: Long): Response<Unit>


    /*  Endpoints de PROYECTOS (Fase D) */

    @GET("api/v1/proyectos")
    suspend fun getProyectos(): Response<List<ProyectoDTO>>

    @GET("api/v1/proyectos/{id}")
    suspend fun getProyecto(
        @Path("id") id: Long,
        @Query("anio") anio: Int,
        @Query("mes") mes: Int
    ): Response<DetalleProyectoDTO>

    /** Agregado de toda la empresa: exige `fichaje:leer:equipo`. */
    @GET("api/v1/proyectos/horas")
    suspend fun getHorasPorProyecto(
        @Query("anio") anio: Int,
        @Query("mes") mes: Int
    ): Response<HorasPorProyectoDTO>

    /** El histórico de asignaciones de una persona, para su perfil. */
    @GET("api/v1/proyectos/empleados/{usuarioId}")
    suspend fun getProyectosDeEmpleado(
        @Path("usuarioId") usuarioId: Long
    ): Response<List<AsignacionProyectoDTO>>

    @POST("api/v1/proyectos")
    suspend fun crearProyecto(@Body peticion: ProyectoRequest): Response<ProyectoDTO>

    @PATCH("api/v1/proyectos/{id}")
    suspend fun editarProyecto(
        @Path("id") id: Long,
        @Body peticion: ProyectoRequest
    ): Response<ProyectoDTO>

    @PATCH("api/v1/proyectos/{id}/estado")
    suspend fun cambiarEstadoProyecto(
        @Path("id") id: Long,
        @Body peticion: EstadoProyectoRequest
    ): Response<ProyectoDTO>

    @DELETE("api/v1/proyectos/{id}")
    suspend fun borrarProyecto(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/proyectos/{id}/asignaciones")
    suspend fun asignarAProyecto(
        @Path("id") proyectoId: Long,
        @Body peticion: AsignarProyectoRequest
    ): Response<AsignacionProyectoDTO>

    /** Cierra la asignación con una fecha de fin; NO la borra. */
    @PATCH("api/v1/proyectos/asignaciones/{asignacionId}")
    suspend fun finalizarAsignacion(
        @Path("asignacionId") asignacionId: Long,
        @Body peticion: FinalizarAsignacionRequest
    ): Response<AsignacionProyectoDTO>


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