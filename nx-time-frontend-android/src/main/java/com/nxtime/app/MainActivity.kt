package com.nxtime.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.nxtime.app.databinding.ActivityMainBinding
import com.nxtime.app.ui.login.HomeActivity
import com.nxtime.app.ui.login.RegistroEmpresaActivity
import com.nxtime.app.ui.login.LoginState
import com.nxtime.app.ui.login.LoginViewModel
import com.nxtime.app.ui.login.LoginViewModelFactory

/**
 * MainActivity: Es la Activity de Login. Es la primera pantalla que ve el usuario al abrir la app.
 */

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * Inyecta el ViewModel usando una Factory.
     */
    private val loginViewModel: LoginViewModel by viewModels {
        val authRepository = (application as NxTimeApplication).authRepository
        LoginViewModelFactory(authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // 1. Recuperamos el SessionManager de la aplicación
        val sessionManager = (application as NxTimeApplication).sessionManager

        // 2. Comprobamos si ya tenemos un token guardado
        if (sessionManager.fetchAuthToken() != null) {

            irAHome()
            return
        }


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // "Observa" el estado del ViewModel para reaccionar y actualizar la UI.

        loginViewModel.loginState.observe(this, Observer { state ->
            when (state) {
                is LoginState.Loading -> {
                    binding.loadingProgressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is LoginState.Success -> {
                    binding.loadingProgressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true

                    irAHome()
                }
                is LoginState.Error -> {
                    binding.loadingProgressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        })

        // Configura el listener del botón de Login

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            Log.d("MainActivity", "Login attempt for email: $email")
            loginViewModel.login(email, password)
        }

        // Configura el Listener del enlace de "Registra tu empresa"

        binding.registerCompanyLink.setOnClickListener {
            val intent = Intent(this, RegistroEmpresaActivity::class.java)
            startActivity(intent)
        }
    }

    // Función auxiliar para navegar a Home y cerrar esta pantalla

    private fun irAHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish() // Cierra MainActivity para que no se pueda volver atrás con el botón "Back"
    }
}