package com.nxtime.app.ui.login

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.R
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.databinding.ActivityHomeBinding
import com.nxtime.app.ui.ausencias.AusenciasActivity
import com.nxtime.app.ui.gestor.ManagerHomeActivity
import com.nxtime.app.ui.historial.HistorialActivity
import com.nxtime.app.ui.solicitud.SolicitudActivity
import com.nxtime.app.ui.usuario.CambiarContrasenaActivity
import com.nxtime.app.MainActivity

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/*
 * HomeActivity es la pantalla principal.
 */

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sessionManager: SessionManager

    /*
     * Inyecta la lógica de la pantalla. El ViewModel gestiona el estado del fichaje
     */

    private val homeViewModel: HomeViewModel by viewModels {
        val authRepository = (application as NxTimeApplication).authRepository
        HomeViewModelFactory(authRepository)
    }

    /*
     * 'onCreate' es la función principal. Se llama al crear la pantalla.
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = (application as NxTimeApplication).sessionManager

        configurarBienvenidaYRoles()

        /*
         * Configura los "oyentes" para todos los botones. Define qué acción o pantalla se abre al pulsar cada botón.
         */

        binding.btnFichar.setOnClickListener { homeViewModel.botonFichajePulsado() }
        binding.btnPausa.setOnClickListener { homeViewModel.botonPausaPulsado() }
        binding.btnVerHistorial.setOnClickListener { startActivity(Intent(this, HistorialActivity::class.java)) }
        binding.btnSolicitarAusencia.setOnClickListener { startActivity(Intent(this, SolicitudActivity::class.java)) }
        binding.btnVerAusencias.setOnClickListener { startActivity(Intent(this, AusenciasActivity::class.java)) }
        binding.btnPanelGestor.setOnClickListener { startActivity(Intent(this, ManagerHomeActivity::class.java)) }
        binding.btnCambiarContrasena.setOnClickListener {
            val intent = Intent(this, CambiarContrasenaActivity::class.java)
            startActivity(intent)
        }


        //  Listener cerrar sesión

        binding.btnCerrarSesion.setOnClickListener {
            cerrarSesion()
        }


        /*
         * "Observa" el estado del fichaje. Cuando el ViewModel dice "el estado ha cambiado", llama a 'actualizarUI' para redibujar la pantalla.
         */

        homeViewModel.fichajeState.observe(this, Observer { state ->
            Log.d("HomeActivity", "Nuevo estado: $state")
            actualizarUI(state)
        })

        homeViewModel.comprobarEstadoJornada()
    }

    // Función para cerrar sesión

    private fun cerrarSesion() {

        // 1. Borrar datos del SessionManager
        sessionManager.clearAuthData()

        // 2. Navegar al Login (MainActivity)
        val intent = Intent(this, MainActivity::class.java)

        // 3. Limpiar la pila de actividades para que no se pueda volver atrás
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    /*
     * Lee el nombre/rol de la sesión para poner el mensaje de "Hola, [Nombre]" y mostrar u ocultar el "Panel de Gestor".
     *
     * Desde la Fase 4 del backend hay 4 roles (EMPLEADO/GESTOR/RRHH/ADMIN,
     * antes solo EMPLEADO/GESTOR): quien registra la empresa ahora es
     * ADMIN, no GESTOR, así que el panel se muestra para cualquier rol
     * de gestión, no solo "GESTOR" exacto.
     */

    private fun configurarBienvenidaYRoles() {
        val nombreUsuario = sessionManager.fetchUserName()
        val rolUsuario = sessionManager.fetchUserRole()

        if (nombreUsuario != null) {
            binding.tvBienvenida.text = "Hola, $nombreUsuario"
        }

        val esRolDeGestion = rolUsuario in setOf("GESTOR", "RRHH", "ADMIN")
        binding.btnPanelGestor.visibility = if (esRolDeGestion) View.VISIBLE else View.GONE
    }

    /*
     * Función de ayuda para convertir la fecha ISO en un texto legible.
     *
     * horaEntrada es un Instant real desde la Fase 3 del backend (antes
     * LocalDateTime "ingenuo"): en JSON lleva sufijo "Z" (UTC), se
     * parsea como Instant y se proyecta a la zona española para
     * mostrarla.
     */

    private fun formatearHoraInicio(rawInstant: String?): String {
        if (rawInstant == null) return ""
        return try {
            val dateTime = Instant.parse(rawInstant).atZone(ZoneId.of("Europe/Madrid"))
            val formatter = DateTimeFormatter.ofPattern("HH:mm 'h'")
            "Jornada iniciada a las: ${dateTime.format(formatter)}"
        } catch (e: DateTimeParseException) {
            Log.e("HomeActivity", "Error al parsear fecha: $rawInstant", e)
            "Jornada iniciada"
        }
    }

     /*
      * Esta es la función reacciona a los cambios de estado y redibuja la pantalla
      */

    private fun actualizarUI(state: FichajeState) {

        val estaCargando = state is FichajeState.Loading
        binding.btnFichar.isEnabled = !estaCargando
        binding.btnPausa.isEnabled = !estaCargando
        binding.btnVerHistorial.isEnabled = !estaCargando
        binding.btnSolicitarAusencia.isEnabled = !estaCargando
        binding.btnVerAusencias.isEnabled = !estaCargando
        binding.btnPanelGestor.isEnabled = !estaCargando
        binding.btnCambiarContrasena.isEnabled = !estaCargando
        binding.btnCerrarSesion.isEnabled = !estaCargando // Desactivar también este botón

        fun setBotonFicharColor(colorResId: Int) {
            val color = ContextCompat.getColor(this, colorResId)
            binding.btnFichar.backgroundTintList = ColorStateList.valueOf(color)
        }

         /*
          * El 'when' decide qué mostrar en la UI basándose en el estado actual del ViewModel.
          */

        when (state) {
            is FichajeState.Loading -> {
                binding.btnFichar.text = "Cargando..."
                setBotonFicharColor(R.color.color_fichar_gris)
                binding.tvHoraInicio.text = ""
                binding.btnPausa.visibility = View.GONE
            }
            is FichajeState.SinJornada -> {
                binding.btnFichar.text = "Iniciar Jornada"
                setBotonFicharColor(R.color.color_fichar_verde)
                binding.tvHoraInicio.text = "Toca para iniciar tu jornada"
                binding.btnPausa.visibility = View.GONE
            }
            is FichajeState.JornadaActiva -> {
                binding.btnFichar.text = "Finalizar Jornada"
                setBotonFicharColor(R.color.color_fichar_rojo)
                binding.tvHoraInicio.text = formatearHoraInicio(state.registro.horaEntrada)
                binding.btnPausa.visibility = View.VISIBLE
                binding.btnPausa.text = "Iniciar Pausa"
            }
            is FichajeState.EnPausa -> {
                binding.btnFichar.text = "Finalizar Jornada"
                setBotonFicharColor(R.color.color_fichar_gris)
                binding.btnFichar.isEnabled = false
                binding.tvHoraInicio.text = "Jornada en pausa"
                binding.btnPausa.visibility = View.VISIBLE
                binding.btnPausa.text = "Reanudar Jornada"
            }
            is FichajeState.Error -> {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                homeViewModel.comprobarEstadoJornada()
            }
        }
    }
}