package com.nxtime.app.data.repository

import com.nxtime.app.data.dto.*
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.network.ApiService
import com.nxtime.app.data.session.SessionManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
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

    override suspend fun solicitarCorreccion(
        fichajeId: Long,
        peticion: CorreccionFichajeRequest
    ): Response<CorreccionDTO> {
        return apiService.solicitarCorreccion(fichajeId, peticion)
    }

    override suspend fun getCorreccionesPendientes(): Response<List<CorreccionDTO>> {
        return apiService.getCorreccionesPendientes()
    }

    override suspend fun getMisCorrecciones(): Response<List<CorreccionDTO>> {
        return apiService.getMisCorrecciones()
    }

    override suspend fun resolverCorreccion(
        correccionId: Long,
        aprobada: Boolean,
        comentario: String?
    ): Response<CorreccionDTO> {
        return apiService.resolverCorreccion(
            correccionId, ResolverCorreccionRequest(aprobada, comentario))
    }

    override suspend fun disputarCorreccion(
        correccionId: Long,
        motivo: String
    ): Response<CorreccionDTO> {
        return apiService.disputarCorreccion(correccionId, DisputaRequest(motivo))
    }

    /* Horas extra (Fase F) */

    override suspend fun getMisHorasExtra(anio: Int?): Response<List<HorasExtraDTO>> {
        return apiService.getMisHorasExtra(anio)
    }

    override suspend fun getHorasExtraDelEquipo(anio: Int?): Response<List<HorasExtraDTO>> {
        return apiService.getHorasExtraDelEquipo(anio)
    }

    override suspend fun revisarHorasExtra(
        avisoId: Long,
        aceptar: Boolean,
        justificacion: String?
    ): Response<HorasExtraDTO> {
        return apiService.revisarHorasExtra(
            avisoId, RevisarHorasExtraRequest(aceptar, justificacion))
    }

    override suspend fun getBolsaHorasExtra(
        usuarioId: Long?,
        anio: Int?
    ): Response<BolsaHorasExtraDTO> {
        return apiService.getBolsaHorasExtra(usuarioId, anio)
    }

    override suspend fun presentarDenuncia(
        categoria: String,
        descripcion: String,
        anonima: Boolean
    ): Response<DenunciaCreadaDTO> {
        return apiService.presentarDenuncia(
            CrearDenunciaRequest(categoria, descripcion, anonima))
    }

    override suspend fun getDenunciaPorCodigo(codigo: String): Response<DenunciaDTO> {
        // Se recorta aquí y no en la pantalla: el código se copia y se
        // pega, y un espacio de más al final no es un código distinto.
        return apiService.getDenunciaPorCodigo(codigo.trim())
    }

    override suspend fun responderDenunciaPorCodigo(
        codigo: String,
        texto: String
    ): Response<DenunciaDTO> {
        return apiService.responderDenunciaPorCodigo(
            codigo.trim(), MensajeDenunciaRequest(texto))
    }

    override suspend fun getMisDenuncias(): Response<List<ResumenDenunciaDTO>> {
        return apiService.getMisDenuncias()
    }

    override suspend fun getMiDenuncia(denunciaId: Long): Response<DenunciaDTO> {
        return apiService.getMiDenuncia(denunciaId)
    }

    override suspend fun responderMiDenuncia(
        denunciaId: Long,
        texto: String
    ): Response<DenunciaDTO> {
        return apiService.responderMiDenuncia(denunciaId, MensajeDenunciaRequest(texto))
    }

    override suspend fun getBandejaDenuncias(): Response<List<ResumenDenunciaDTO>> {
        return apiService.getBandejaDenuncias()
    }

    override suspend fun getDenuncia(denunciaId: Long): Response<DenunciaDTO> {
        return apiService.getDenuncia(denunciaId)
    }

    override suspend fun responderDenunciaComoInstructor(
        denunciaId: Long,
        texto: String
    ): Response<DenunciaDTO> {
        return apiService.responderDenunciaComoInstructor(
            denunciaId, MensajeDenunciaRequest(texto))
    }

    override suspend fun cambiarEstadoDenuncia(
        denunciaId: Long,
        estado: String,
        conclusion: String?
    ): Response<DenunciaDTO> {
        return apiService.cambiarEstadoDenuncia(
            denunciaId, CambiarEstadoDenunciaRequest(estado, conclusion))
    }

    override suspend fun getAuditoriaFichaje(
        fichajeId: Long
    ): Response<List<AuditoriaFichajeDTO>> {
        return apiService.getAuditoriaFichaje(fichajeId)
    }

    override suspend fun getPanelEmpresa(): Response<PanelEmpresaDTO> {
        return apiService.getPanelEmpresa()
    }

    override suspend fun cambiarEstadoEmpleado(
        empleadoId: Long,
        activo: Boolean
    ): Response<Unit> {
        return apiService.cambiarEstadoEmpleado(
            empleadoId,
            CambioEstadoEmpleadoRequest(activo)
        )
    }

    override suspend fun descargarExcelDeHoras(anio: Int, mes: Int): Response<ResponseBody> {
        return apiService.descargarExcelDeHoras(anio, mes)
    }

    override suspend fun descargarPdfMensual(
        empleadoId: Long,
        anio: Int,
        mes: Int
    ): Response<ResponseBody> {
        return apiService.descargarPdfMensual(empleadoId, anio, mes)
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

    // El cuerpo se arma aquí y no en el ViewModel, igual que en
    // cambiarEstadoEmpleado: la forma del request es cosa de la capa de
    // datos, no de la pantalla.
    override suspend fun configurarFichaEmpleado(
        empleadoId: Long,
        horasSemanales: String?,
        diasVacaciones: Int?
    ): Response<EmpleadoSimpleDTO> {
        return apiService.configurarFichaEmpleado(
            empleadoId,
            FichaEmpleadoRequest(horasSemanales, diasVacaciones)
        )
    }

    override suspend fun getMiPerfil(): Response<PerfilDTO> {
        return apiService.getMiPerfil()
    }

    override suspend fun actualizarMiPerfil(
        nombre: String?,
        apellidos: String?,
        fechaNacimiento: String?,
        puesto: String?
    ): Response<PerfilDTO> {
        return apiService.actualizarMiPerfil(
            ActualizarPerfilRequest(nombre, apellidos, fechaNacimiento, puesto)
        )
    }

    override suspend fun getDepartamentos(): Response<List<DepartamentoDTO>> {
        return apiService.getDepartamentos()
    }

    override suspend fun crearDepartamento(nombre: String): Response<DepartamentoDTO> {
        return apiService.crearDepartamento(DepartamentoRequest(nombre))
    }

    override suspend fun borrarDepartamento(id: Long): Response<Unit> {
        return apiService.borrarDepartamento(id)
    }

    override suspend fun asignarDepartamento(usuarioId: Long, departamentoId: Long?): Response<PerfilDTO> {
        return apiService.asignarDepartamento(usuarioId, AsignarDepartamentoRequest(departamentoId))
    }

    override suspend fun getMisAdjuntos(): Response<List<AdjuntoDTO>> {
        return apiService.getMisAdjuntos()
    }

    /**
     * El multipart se arma aquí y no en el ViewModel: la forma del
     * cuerpo es cosa de la capa de datos, como en el resto.
     *
     * El `mime` que se manda es solo informativo -- el servidor decide
     * el tipo real mirando los primeros bytes --, pero se envía el que
     * el sistema declara para el fichero elegido en vez de un
     * `application/octet-stream` genérico.
     */
    override suspend fun subirAdjunto(
        contenido: ByteArray,
        nombre: String,
        mime: String,
        tipo: String
    ): Response<AdjuntoDTO> {
        val cuerpo = contenido.toRequestBody(mime.toMediaTypeOrNull())
        return apiService.subirAdjunto(
            MultipartBody.Part.createFormData("fichero", nombre, cuerpo),
            tipo.toRequestBody("text/plain".toMediaTypeOrNull())
        )
    }

    override suspend fun descargarAdjunto(adjuntoId: Long): Response<ResponseBody> {
        return apiService.descargarAdjunto(adjuntoId)
    }

    override suspend fun borrarAdjunto(adjuntoId: Long): Response<Unit> {
        return apiService.borrarAdjunto(adjuntoId)
    }

    override suspend fun getProyectos(): Response<List<ProyectoDTO>> {
        return apiService.getProyectos()
    }

    override suspend fun getProyecto(id: Long, anio: Int, mes: Int): Response<DetalleProyectoDTO> {
        return apiService.getProyecto(id, anio, mes)
    }

    override suspend fun getHorasPorProyecto(anio: Int, mes: Int): Response<HorasPorProyectoDTO> {
        return apiService.getHorasPorProyecto(anio, mes)
    }

    override suspend fun getProyectosDeEmpleado(usuarioId: Long): Response<List<AsignacionProyectoDTO>> {
        return apiService.getProyectosDeEmpleado(usuarioId)
    }

    override suspend fun crearProyecto(peticion: ProyectoRequest): Response<ProyectoDTO> {
        return apiService.crearProyecto(peticion)
    }

    override suspend fun editarProyecto(id: Long, peticion: ProyectoRequest): Response<ProyectoDTO> {
        return apiService.editarProyecto(id, peticion)
    }

    override suspend fun cambiarEstadoProyecto(id: Long, activo: Boolean): Response<ProyectoDTO> {
        return apiService.cambiarEstadoProyecto(id, EstadoProyectoRequest(activo))
    }

    override suspend fun borrarProyecto(id: Long): Response<Unit> {
        return apiService.borrarProyecto(id)
    }

    override suspend fun asignarAProyecto(
        proyectoId: Long,
        peticion: AsignarProyectoRequest
    ): Response<AsignacionProyectoDTO> {
        return apiService.asignarAProyecto(proyectoId, peticion)
    }

    override suspend fun finalizarAsignacion(
        asignacionId: Long,
        fechaFin: String
    ): Response<AsignacionProyectoDTO> {
        return apiService.finalizarAsignacion(asignacionId, FinalizarAsignacionRequest(fechaFin))
    }

    override suspend fun getCalendario(anio: Int, mes: Int, equipo: Boolean): Response<CalendarioDTO> {
        return apiService.getCalendario(anio, mes, equipo)
    }

    override suspend fun crearFestivo(peticion: FestivoRequest): Response<FestivoDTO> {
        return apiService.crearFestivo(peticion)
    }

    override suspend fun editarFestivo(id: Long, peticion: FestivoRequest): Response<FestivoDTO> {
        return apiService.editarFestivo(id, peticion)
    }

    override suspend fun borrarFestivo(id: Long): Response<Unit> {
        return apiService.borrarFestivo(id)
    }

    override suspend fun getAvisos(): Response<List<AvisoDTO>> {
        return apiService.getAvisos()
    }

    override suspend fun getContadorAvisos(): Response<ContadorAvisosDTO> {
        return apiService.getContadorAvisos()
    }

    override suspend fun marcarAvisoLeido(avisoId: Long): Response<Unit> {
        return apiService.marcarAvisoLeido(avisoId)
    }

    override suspend fun marcarTodosLosAvisosLeidos(): Response<Unit> {
        return apiService.marcarTodosLosAvisosLeidos()
    }

}