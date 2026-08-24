package com.nxtime.app.ui.historial

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.databinding.ActivityHistorialBinding

/*
 * HistorialActivity: Es la "pantalla" que muestra el historial de fichajes del propio empleado.
 */

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private lateinit var historialAdapter: HistorialAdapter

        /*
         * Inyecta la lógica de la pantalla usando una Factory.
         */

    private val historialViewModel: HistorialViewModel by viewModels {
        val authRepository = (application as NxTimeApplication).authRepository
        HistorialViewModelFactory(authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)


        setupRecyclerView()


        historialViewModel.historialState.observe(this, Observer { state ->
            Log.d("HistorialActivity", "Nuevo estado: $state")
            actualizarUI(state)
        })


        historialViewModel.cargarHistorial()
    }

        /*
         * Prepara el RecyclerView: le dice cómo mostrar los elementos y le asigna su adaptador
         */

    private fun setupRecyclerView() {

        historialAdapter = HistorialAdapter(emptyList())

        binding.rvHistorial.apply {

            layoutManager = LinearLayoutManager(this@HistorialActivity)
            adapter = historialAdapter
        }
    }

        /*
         * Actualiza la vista según el estado que le envía el ViewModel.
         */

    private fun actualizarUI(state: HistorialState) {
        when (state) {
            is HistorialState.Loading -> {

                Log.d("HistorialActivity", "Cargando historial...")
            }
            is HistorialState.Success -> {

                Log.d("HistorialActivity", "Datos recibidos: ${state.registros.size} items")

                (binding.rvHistorial.adapter as HistorialAdapter).actualizarLista(state.registros)
            }
            is HistorialState.Error -> {
                Log.e("HistorialActivity", "Error: ${state.message}")
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }


}