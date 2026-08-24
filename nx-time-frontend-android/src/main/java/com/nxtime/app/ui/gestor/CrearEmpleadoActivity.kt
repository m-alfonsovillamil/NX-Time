package com.nxtime.app.ui.gestor

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.databinding.ActivityCrearEmpleadoBinding

/*
 * CrearEmpleadoActivity: Es la "pantalla" que usa el Gestor para rellenar el formulario y crear un nuevo Empleado.
 */

class CrearEmpleadoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrearEmpleadoBinding

     /*
      * Inyecta el ViewModel (la lógica de la pantalla) usando una Factory.
      */

    private val viewModel: CrearEmpleadoViewModel by viewModels {
        val authRepository = (application as NxTimeApplication).authRepository
        CrearEmpleadoViewModelFactory(authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCrearEmpleadoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

        /*
         * Configura el "oyente" del botón "Crear Empleado".
         */

    private fun setupListeners() {
        binding.crearEmpleadoButton.setOnClickListener {
            val nombre = binding.empleadoNombreEditText.text.toString().trim()
            val email = binding.empleadoEmailEditText.text.toString().trim()
            val contrasena = binding.empleadoPasswordEditText.text.toString().trim()


            if (nombre.isEmpty() || email.isEmpty() || contrasena.isEmpty()) {
                Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            viewModel.crearEmpleado(nombre, email, contrasena)
        }
    }

        /*
         * Observa los LiveData del ViewModel y reacciona a los cambios.
         */

    private fun setupObservers() {
        // Observa el estado de "Cargando"
        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.crearEmpleadoProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.crearEmpleadoButton.isEnabled = !isLoading
        })

        // Observa si la creación fue exitosa
        viewModel.creacionExitosa.observe(this, Observer { isSuccess ->
            if (isSuccess) {
                Toast.makeText(this, "¡Empleado creado con éxito!", Toast.LENGTH_LONG).show()
                // Cierra esta actividad y vuelve al panel de gestor
                finish()
            }
        })

        // Observa si hay errores
        viewModel.error.observe(this, Observer { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_LONG).show()
            }
        })
    }
}