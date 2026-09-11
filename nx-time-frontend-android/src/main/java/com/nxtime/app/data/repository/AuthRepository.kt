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

    /* Códigos de acceso (ADR 014): recuperar la contraseña o elegirla la primera vez */
    suspend fun solicitarCodigoAcceso(email: String): Response<Unit>
    suspend fun restablecerContrasena(
        email: String,
        codigo: String,
        contrasenaNueva: String
    ): Response<Unit>

    /* Funciones de Fichaje (Empleado) */
    suspend fun getRegistroActivo(): Response<Registro?>
    suspend fun registrarFichaje(peticion: PeticionFichaje): Response<Registro>
    suspend fun getHistorial(): Response<List<Registro>>
    suspend fun getResumenPersonal(): Response<ResumenPersonalDTO>

    /* Cumplimiento normativo (RRHH/ADMIN) */
    /** Pide una correccion; no la aplica. Ver ApiService. */
    suspend fun solicitarCorreccion(
        fichajeId: Long,
        peticion: CorreccionFichajeRequest
    ): Response<CorreccionDTO>

    suspend fun getCorreccionesPendientes(): Response<List<CorreccionDTO>>
    suspend fun getMisCorrecciones(): Response<List<CorreccionDTO>>
    suspend fun resolverCorreccion(
        correccionId: Long,
        aprobada: Boolean,
        comentario: String?
    ): Response<CorreccionDTO>
    suspend fun disputarCorreccion(correccionId: Long, motivo: String): Response<CorreccionDTO>

    /* Horas extra (Fase F) */
    suspend fun getMisHorasExtra(anio: Int? = null): Response<List<HorasExtraDTO>>
    suspend fun getHorasExtraDelEquipo(anio: Int? = null): Response<List<HorasExtraDTO>>
    suspend fun revisarHorasExtra(
        avisoId: Long,
        aceptar: Boolean,
        justificacion: String? = null
    ): Response<HorasExtraDTO>
    suspend fun getBolsaHorasExtra(usuarioId: Long? = null, anio: Int? = null): Response<BolsaHorasExtraDTO>

    /* Canal de denuncias (Fase G) */
    suspend fun presentarDenuncia(
        categoria: String,
        descripcion: String,
        anonima: Boolean
    ): Response<DenunciaCreadaDTO>
    suspend fun getDenunciaPorCodigo(codigo: String): Response<DenunciaDTO>
    suspend fun responderDenunciaPorCodigo(codigo: String, texto: String): Response<DenunciaDTO>
    suspend fun getMisDenuncias(): Response<List<ResumenDenunciaDTO>>
    suspend fun getMiDenuncia(denunciaId: Long): Response<DenunciaDTO>
    suspend fun responderMiDenuncia(denunciaId: Long, texto: String): Response<DenunciaDTO>
    suspend fun getBandejaDenuncias(): Response<List<ResumenDenunciaDTO>>
    suspend fun getDenuncia(denunciaId: Long): Response<DenunciaDTO>
    suspend fun responderDenunciaComoInstructor(
        denunciaId: Long,
        texto: String
    ): Response<DenunciaDTO>
    suspend fun cambiarEstadoDenuncia(
        denunciaId: Long,
        estado: String,
        conclusion: String? = null
    ): Response<DenunciaDTO>

    /* Ofertas internas y candidaturas (Fase H) */
    suspend fun getOfertas(): Response<List<OfertaDTO>>
    suspend fun getOfertasDeGestion(): Response<List<OfertaDTO>>
    suspend fun getOferta(ofertaId: Long): Response<OfertaDTO>
    suspend fun crearOferta(
        titulo: String,
        descripcion: String,
        puesto: String? = null,
        fechaCierre: String? = null
    ): Response<OfertaDTO>
    suspend fun cambiarEstadoOferta(ofertaId: Long, estado: String): Response<OfertaDTO>
    suspend fun presentarCandidatura(ofertaId: Long, carta: String?): Response<CandidaturaDTO>
    suspend fun getCandidaturasDeOferta(ofertaId: Long): Response<List<CandidaturaDTO>>
    suspend fun getMisCandidaturas(): Response<List<CandidaturaDTO>>
    suspend fun valorarCandidatura(
        candidaturaId: Long,
        estado: String,
        comentario: String? = null
    ): Response<CandidaturaDTO>

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

    /* Funciones de Adjuntos (Fase B2) */
    suspend fun getMisAdjuntos(): Response<List<AdjuntoDTO>>
    suspend fun subirAdjunto(
        contenido: ByteArray,
        nombre: String,
        mime: String,
        tipo: String
    ): Response<AdjuntoDTO>
    suspend fun descargarAdjunto(adjuntoId: Long): Response<ResponseBody>
    suspend fun borrarAdjunto(adjuntoId: Long): Response<Unit>

    /* Funciones de Calendario (Fase C) */
    suspend fun getCalendario(anio: Int, mes: Int, equipo: Boolean): Response<CalendarioDTO>
    suspend fun crearFestivo(peticion: FestivoRequest): Response<FestivoDTO>
    suspend fun editarFestivo(id: Long, peticion: FestivoRequest): Response<FestivoDTO>
    suspend fun borrarFestivo(id: Long): Response<Unit>

    /* Funciones de Proyectos (Fase D) */
    suspend fun getProyectos(): Response<List<ProyectoDTO>>
    suspend fun getProyecto(id: Long, anio: Int, mes: Int): Response<DetalleProyectoDTO>
    suspend fun getHorasPorProyecto(anio: Int, mes: Int): Response<HorasPorProyectoDTO>
    suspend fun getProyectosDeEmpleado(usuarioId: Long): Response<List<AsignacionProyectoDTO>>
    suspend fun crearProyecto(peticion: ProyectoRequest): Response<ProyectoDTO>
    suspend fun editarProyecto(id: Long, peticion: ProyectoRequest): Response<ProyectoDTO>
    suspend fun cambiarEstadoProyecto(id: Long, activo: Boolean): Response<ProyectoDTO>
    suspend fun borrarProyecto(id: Long): Response<Unit>
    suspend fun asignarAProyecto(
        proyectoId: Long,
        peticion: AsignarProyectoRequest
    ): Response<AsignacionProyectoDTO>
    suspend fun finalizarAsignacion(
        asignacionId: Long,
        fechaFin: String
    ): Response<AsignacionProyectoDTO>

    /* Funciones de Avisos */
    suspend fun getAvisos(): Response<List<AvisoDTO>>
    suspend fun getContadorAvisos(): Response<ContadorAvisosDTO>
    suspend fun marcarAvisoLeido(avisoId: Long): Response<Unit>
    suspend fun marcarTodosLosAvisosLeidos(): Response<Unit>

}