package com.nxtime.app.ui.gestor


import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.databinding.ActivityGestorBinding

/*
 * GestorActivity: Es la Activity que usa el Gestor para ver y gestionar las solicitudes de ausencia PENDIENTES.
 */
class GestorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestorBinding
    private lateinit var gestorAdapter: GestorAusenciasAdapter

    /*
     * Inyecta el ViewModel (lógica) usando una Factory.
     */

    private val viewModel: GestorViewModel by viewModels {
        GestorViewModelFactory((application as NxTimeApplication).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Configura la lista (RecyclerView)
        setupRecyclerView()

        // 2. Observa los datos del ViewModel para reaccionar a cambios
        viewModel.gestorState.observe(this, Observer { state ->
            Log.d("GestorActivity", "Nuevo estado: $state")
            actualizarUI(state)
        })

        // 3. Pide al ViewModel que cargue las peticiones pendientes
        viewModel.cargarPeticionesPendientes()


    }

    /*
     * Prepara el RecyclerView. Le pasa al adaptador las acciones que debe ejecutar cuando se pulse "Aprobar" o "Rechazar".
     */

    private fun setupRecyclerView() {
        gestorAdapter = GestorAusenciasAdapter(
            emptyList(),
            onAprobarClicked = { peticion ->
                Log.d("GestorActivity", "Aprobando petición ID: ${peticion.id}")
                viewModel.aprobarPeticion(peticion.id)
            },
            onRechazarClicked = { peticion ->
                Log.d("GestorActivity", "Rechazando petición ID: ${peticion.id}")
                viewModel.rechazarPeticion(peticion.id)
            }
        )

        binding.rvPeticionesGestor.apply {
            layoutManager = LinearLayoutManager(this@GestorActivity)
            adapter = gestorAdapter
        }
    }

    /*
     * Actualiza la UI (la vista) según el estado que le envía el ViewModel.
     */

    private fun actualizarUI(state: GestorState) {
        when (state) {
            is GestorState.Loading -> {
                Log.d("GestorActivity", "Cargando peticiones pendientes...")
                binding.tvNoHayPeticiones.visibility = View.GONE
            }
            is GestorState.Success -> {
                Log.d("GestorActivity", "Datos recibidos: ${state.peticiones.size} items")

                if (state.peticiones.isEmpty()) {
                    binding.tvNoHayPeticiones.visibility = View.VISIBLE
                    binding.rvPeticionesGestor.visibility = View.GONE
                } else {
                    binding.tvNoHayPeticiones.visibility = View.GONE
                    binding.rvPeticionesGestor.visibility = View.VISIBLE
                    gestorAdapter.actualizarLista(state.peticiones)
                }
            }
            is GestorState.Error -> {
                Log.e("GestorActivity", "Error: ${state.message}")
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                binding.tvNoHayPeticiones.text = "Error al cargar peticiones"
                binding.tvNoHayPeticiones.visibility = View.VISIBLE
            }
        }
    }
}