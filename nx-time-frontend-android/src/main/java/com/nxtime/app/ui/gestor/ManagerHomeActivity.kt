package com.nxtime.app.ui.gestor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nxtime.app.databinding.ActivityManagerHomeBinding
import com.nxtime.app.ui.gestor.historial.GestorHistorialActivity
import com.nxtime.app.ui.gestor.ausencias.GestorAusenciasAprobadasActivity

/*
 * ManagerHomeActivity: Es la Activity principal del Gestor, que funciona como un panel de navegación.
 */

class ManagerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /*
         * Configura los listeners para cada botón. Define qué pantalla se abre al pulsar cada botón.
         */

        // 1. Botón para ir a Solicitudes Pendientes
        binding.btnGestionarAusencias.setOnClickListener {
            val intent = Intent(this, GestorActivity::class.java)
            startActivity(intent)
        }

        // 2. Botón para ir al Historial del Equipo
        binding.btnVerHistorialEquipo.setOnClickListener {
            val intent = Intent(this, GestorHistorialActivity::class.java)
            startActivity(intent)
        }

        // 3. Botón flotante para ir a Crear Empleado
        binding.fabCrearEmpleado.setOnClickListener {
            val intent = Intent(this, CrearEmpleadoActivity::class.java)
            startActivity(intent)
        }

        // 4. Botón para ir a Ausencias Aprobadas
        binding.btnVerAusenciasAprobadas.setOnClickListener {
            val intent = Intent(this, GestorAusenciasAprobadasActivity::class.java)
            startActivity(intent)
        }
        binding.btnCrearGestor.setOnClickListener {
            startActivity(Intent(this, CrearGestorActivity::class.java))
        }

    }
}