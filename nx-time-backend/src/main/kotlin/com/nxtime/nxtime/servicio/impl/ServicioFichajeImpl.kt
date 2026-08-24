package com.nxtime.nxtime.servicio.impl

import com.nxtime.nxtime.dominio.Registros
import com.nxtime.nxtime.dto.PeticionFichaje
import com.nxtime.nxtime.dto.RegistroEquipoDTO
import com.nxtime.nxtime.dto.UsuarioSimpleDTO
import com.nxtime.nxtime.repositorio.RegistroRepositorio
import com.nxtime.nxtime.repositorio.UsuarioRepositorio
import com.nxtime.nxtime.servicio.ServicioFichaje
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Contiene toda la lógica de negocio para fichar y consultar fichajes.
 */
@Service
class ServicioFichajeImpl(

    private val registroRepositorio: RegistroRepositorio,
    private val usuarioRepositorio: UsuarioRepositorio
) : ServicioFichaje {

    /*
     * Lógica principal para manejar el botón de fichaje de la app.
     */
    override fun registrarFichaje(emailUsuario: String, peticion: PeticionFichaje): Registros {
        val usuario = usuarioRepositorio.findByEmail(emailUsuario)
            .orElseThrow { EntityNotFoundException("Usuario no encontrado con email: $emailUsuario") }


        val registroActivo = registroRepositorio.findByUsuarioAndHoraSalidaIsNull(usuario).orElse(null)

        return when (peticion.tipo) {
            "INICIO" -> {
                if (registroActivo != null) throw IllegalStateException("Ya hay una jornada activa.")
                val nuevoRegistro = Registros(
                    usuario = usuario,
                    horaEntrada = LocalDateTime.now()
                )
                registroRepositorio.save(nuevoRegistro)
            }
            "FIN" -> {
                if (registroActivo == null) throw IllegalStateException("No hay jornada activa para finalizar.")
                if (registroActivo.enPausa) throw IllegalStateException("No se puede finalizar la jornada mientras está en pausa.")
                registroActivo.horaSalida = LocalDateTime.now()
                registroRepositorio.save(registroActivo)
            }
            "PAUSA_INICIO" -> {
                if (registroActivo == null) throw IllegalStateException("No hay jornada activa para pausar.")
                if (registroActivo.enPausa) throw IllegalStateException("La jornada ya está en pausa.")

                registroActivo.enPausa = true
                registroActivo.inicioPausaActual = LocalDateTime.now()

                registroRepositorio.save(registroActivo)
            }
            "PAUSA_FIN" -> {
                if (registroActivo == null) throw IllegalStateException("No hay jornada activa.")
                if (!registroActivo.enPausa) throw IllegalStateException("La jornada no está en pausa.")

                /*
                 * Lógica de fin de pausa
                 */
                val inicioPausa = registroActivo.inicioPausaActual
                    ?: throw IllegalStateException("Error: No se encontró el inicio de la pausa.")

                val ahora = LocalDateTime.now()
                val duracionPausa = Duration.between(inicioPausa, ahora)

                registroActivo.minutosPausaAcumulados += duracionPausa.toMinutes()

                registroActivo.enPausa = false
                registroActivo.inicioPausaActual = null

                registroRepositorio.save(registroActivo)
            }
            else -> throw IllegalArgumentException("Acción no válida: ${peticion.tipo}")
        }
    }

    /*
     * Lógica para que la app sepa el estado actual del usuario.
     */

    override fun getRegistroActivo(emailUsuario: String): Registros? {
        val usuario = usuarioRepositorio.findByEmail(emailUsuario)
            .orElseThrow { EntityNotFoundException("Usuario no encontrado") }
        return registroRepositorio.findByUsuarioAndHoraSalidaIsNull(usuario).orElse(null)
    }

    /*
     * Lógica para que el empleado vea su PROPIO historial.
     */

    override fun getHistorial(emailUsuario: String): List<Registros> {
        val usuario = usuarioRepositorio.findByEmail(emailUsuario)
            .orElseThrow { EntityNotFoundException("Usuario no encontrado") }
        return registroRepositorio.findByUsuarioOrderByHoraEntradaDesc(usuario)
    }

    /*
     * Lógica para que el GESTOR vea el historial de todo su equipo.
     */

    override fun getHistorialEquipo(emailGestor: String): List<RegistroEquipoDTO> {
        val gestor = usuarioRepositorio.findByEmail(emailGestor)
            .orElseThrow { EntityNotFoundException("Gestor no encontrado con email: $emailGestor") }

        val empresa = gestor.empresa
        val empleadosEmpresa = usuarioRepositorio.findByEmpresa(empresa)
        val registrosDeLaEmpresa = registroRepositorio.findByUsuarioInOrderByHoraEntradaDesc(empleadosEmpresa)

        return registrosDeLaEmpresa.map { it.toRegistroEquipoDTO() }
    }

    /*
     * Convierte la entidad de BBDD 'Registros' en un DTO 'RegistroEquipoDTO' para enviar a la app.
     */

    private fun Registros.toRegistroEquipoDTO(): RegistroEquipoDTO {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        return RegistroEquipoDTO(
            id = this.id,
            horaEntrada = this.horaEntrada.toLocalTime().format(timeFormatter),
            horaSalida = this.horaSalida?.toLocalTime()?.format(timeFormatter),
            fecha = this.horaEntrada.toLocalDate().format(dateFormatter),
            usuario = UsuarioSimpleDTO(
                nombre = this.usuario.nombre
            ),
            minutosPausaAcumulados = this.minutosPausaAcumulados
        )
    }
}