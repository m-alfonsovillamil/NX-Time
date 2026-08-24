package com.nxtime.app.ui.ausencias

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.databinding.ActivityAusenciasBinding

/*
 * Activity que muestra la lista de ausencias del empleado
 */
class AusenciasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAusenciasBinding
    private lateinit var ausenciasAdapter: AusenciasAdapter

    /*
     * Se encarga de crear/mantener el ViewModel vivo durante la vida de la pantalla.
     */
    private val viewModel: AusenciasViewModel by viewModels {
        AusenciasViewModelFactory((application as NxTimeApplication).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAusenciasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Configura el RecyclerView
        setupRecyclerView()

        // 2. Le dice a la Activity que esté atento a los cambios

        viewModel.ausenciasState.observe(this, Observer { state ->
            Log.d("AusenciasActivity", "Nuevo estado: $state")
            actualizarUI(state)
        })

        // 3. Le pide al ViewModel que empiece a cargar los datos.
        viewModel.cargarMisPeticiones()
    }

    /*
     * Prepara el RecyclerView (le dice cómo mostrar los elementos y le asigna su adaptador).
     */
    private fun setupRecyclerView() {
        ausenciasAdapter = AusenciasAdapter(emptyList())
        binding.rvAusencias.apply {
            layoutManager = LinearLayoutManager(this@AusenciasActivity)
            adapter = ausenciasAdapter
        }
    }

    /*
     * Esta función reacciona a los cambios de estado
     */
    private fun actualizarUI(state: AusenciasState) {
        when (state) {
            is AusenciasState.Loading -> {
                // TODO: Mostrar un ProgressBar
                Log.d("AusenciasActivity", "Cargando mis peticiones...")
            }
            is AusenciasState.Success -> {
                // TODO: Ocultar el ProgressBar
                // Pasa la lista de peticiones al adaptador para que las "pinte".
                ausenciasAdapter.actualizarLista(state.peticiones)
            }
            is AusenciasState.Error -> {
                // TODO: Ocultar el ProgressBar y mostrar un error
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}