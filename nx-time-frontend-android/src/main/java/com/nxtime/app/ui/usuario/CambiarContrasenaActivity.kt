package com.nxtime.app.ui.usuario

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.databinding.ActivityCambiarContrasenaBinding

/**
 * CambiarContrasenaActivity: Es la Activity que muestra el formulario para que un usuario cambie su contraseña.
 */

class CambiarContrasenaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCambiarContrasenaBinding

    /**
     * Inyecta el ViewModel usando una Factory.
     */

    private val viewModel: CambiarContrasenaViewModel by viewModels {
        val authRepository = (application as NxTimeApplication).authRepository
        CambiarContrasenaViewModelFactory(authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCambiarContrasenaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

    /**
     * Configura el listener del botón "Actualizar Contraseña".
     */

    private fun setupListeners() {
        binding.actualizarPasswordButton.setOnClickListener {
            val antigua = binding.antiguaPasswordEditText.text.toString()
            val nueva = binding.nuevaPasswordEditText.text.toString()
            val confirmar = binding.confirmarPasswordEditText.text.toString()


            if (antigua.isEmpty() || nueva.isEmpty() || confirmar.isEmpty()) {
                Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (nueva.length < 6) { // O la validación que tengas
                Toast.makeText(this, "La nueva contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (nueva != confirmar) {
                Toast.makeText(this, "Las nuevas contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            viewModel.cambiarContrasena(antigua, nueva)
        }
    }

    /**
     * "Observa" los LiveData del ViewModel y reacciona a los cambios en la UI.
     */

    private fun setupObservers() {
        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.cambiarPasswordProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.actualizarPasswordButton.isEnabled = !isLoading
        })

        viewModel.cambioExitoso.observe(this, Observer { isSuccess ->
            if (isSuccess) {
                Toast.makeText(this, "¡Contraseña actualizada con éxito!", Toast.LENGTH_LONG).show()
                finish()
            }
        })

        viewModel.error.observe(this, Observer { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_LONG).show()
            }
        })
    }
}