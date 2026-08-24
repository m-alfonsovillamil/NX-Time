package com.nxtime.app.ui.gestor

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.databinding.ActivityCrearGestorBinding

/*
 * Pantalla exclusiva para que un GESTOR cree otro GESTOR
 */

class CrearGestorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrearGestorBinding

    // Inyectamos el repositorio en el ViewModel para poder llamar a la API.
    private val viewModel: CrearGestorViewModel by viewModels {
        CrearGestorViewModelFactory((application as NxTimeApplication).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrearGestorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Listener del botón "Crear Gestor"

        binding.crearGestorButton.setOnClickListener {
            val nombre = binding.gestorNombreEditText.text.toString().trim()
            val email = binding.gestorEmailEditText.text.toString().trim()
            val pass = binding.gestorPasswordEditText.text.toString().trim()

            // Validación simple y llamada a la lógica de negocio

            if(nombre.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()){
                viewModel.crearGestor(nombre, email, pass)
            } else {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Observa si está cargando para mostrar la barra de progreso

        viewModel.isLoading.observe(this) { loading ->
            binding.crearGestorProgressBar.visibility = if(loading) View.VISIBLE else View.GONE
            binding.crearGestorButton.isEnabled = !loading
        }

        // Si la creación fue exitosa, avisa y cierra la pantalla

        viewModel.creacionExitosa.observe(this) { success ->
            if(success) {
                Toast.makeText(this, "Gestor creado con éxito", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        // Muestra errores si ocurren

        viewModel.error.observe(this) { msg ->
            if(msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }
}