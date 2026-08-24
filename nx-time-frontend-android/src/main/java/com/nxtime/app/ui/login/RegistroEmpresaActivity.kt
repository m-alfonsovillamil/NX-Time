package com.nxtime.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.databinding.ActivityRegistroEmpresaBinding
import com.nxtime.app.ui.gestor.ManagerHomeActivity

/**
 * RegistroEmpresaActivity: Es la "pantalla" que controla el formulario de registro de una nueva empresa y gestor.
 */

class RegistroEmpresaActivity : AppCompatActivity() {


    private lateinit var binding: ActivityRegistroEmpresaBinding

        /**
         * Inyecta el ViewModel (la lógica de la pantalla) usando una Factory.
         */

    private val viewModel: RegistroEmpresaViewModel by viewModels {
        val authRepository = (application as NxTimeApplication).authRepository
        RegistroEmpresaViewModelFactory(authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegistroEmpresaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

    /**
     * Configura el listener del botón "Crear Cuenta".
     */

    private fun setupListeners() {
        binding.registerCompanyButton.setOnClickListener {

            val nombreEmpresa = binding.empresaNombreEditText.text.toString().trim()
            val nombreGestor = binding.gestorNombreEditText.text.toString().trim()
            val email = binding.gestorEmailEditText.text.toString().trim()
            val password = binding.gestorPasswordEditText.text.toString().trim()


            if (nombreEmpresa.isEmpty() || nombreGestor.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            viewModel.registrarEmpresa(nombreEmpresa, nombreGestor, email, password)
        }
    }

    /**
     * "Observa" los LiveData del ViewModel y reacciona a los cambios en la UI.
     */

    private fun setupObservers() {

        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.registroProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.registerCompanyButton.isEnabled = !isLoading
        })


        viewModel.registroExitoso.observe(this, Observer { isSuccess ->
            if (isSuccess) {
                Toast.makeText(this, "¡Registro y login exitosos!", Toast.LENGTH_LONG).show()


                /**
                 * Si fue exitoso, navega al panel del Gestor y limpia la pila
                 */

                val intent = Intent(this, ManagerHomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish() // Cierra esta actividad
            }
        })


        viewModel.error.observe(this, Observer { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_LONG).show()
            }
        })
    }
}