package com.nxtime.app.ui.gestor.ausencias

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.databinding.ActivityGestorAusenciasAprobadasBinding
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.ui.ausencias.AusenciasAdapter


/*
 * GestorAusenciasAprobadasActivity: Es la Activity que usa el Gestor para ver el historial de ausencias (aprobadas/rechazadas) y filtrarlas por empleado.
 */
class GestorAusenciasAprobadasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestorAusenciasAprobadasBinding
    private lateinit var ausenciasAdapter: AusenciasAdapter
    private lateinit var empleadosAdapter: ArrayAdapter<String>

        /*
         * Implementa la lógica de la pantalla usando una Factory.
         */
    private val viewModel: GestorAusenciasAprobadasViewModel by viewModels {
        GestorAusenciasAprobadasViewModelFactory((application as NxTimeApplication).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestorAusenciasAprobadasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupDropdownListener()

        viewModel.cargarDatos()
    }
        /*
         * Prepara el RecyclerView. Reutiliza el 'AusenciasAdapter' que también usa el empleado.
         */
    private fun setupRecyclerView() {

        ausenciasAdapter = AusenciasAdapter(emptyList())
        binding.rvAusenciasAprobadas.apply {
            layoutManager = LinearLayoutManager(this@GestorAusenciasAprobadasActivity)
            adapter = ausenciasAdapter
        }
    }
        /*
         * Configura todos los "oyentes" del ViewModel.
         */
    private fun setupObservers() {
        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.ausenciasProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.rvAusenciasAprobadas.visibility = if (isLoading) View.GONE else View.VISIBLE
            binding.tvNoHayAusencias.visibility = View.GONE
        })

        viewModel.error.observe(this, Observer { error ->
            if (error != null) {
                Log.e("GestorAusencias", "Error: $error")
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        })

        viewModel.empleados.observe(this, Observer { empleados ->
            Log.d("GestorAusencias", "Empleados recibidos: ${empleados.size}")
            setupDropdownMenu(empleados)
        })

        viewModel.ausenciasFiltradas.observe(this, Observer { ausencias ->
            Log.d("GestorAusencias", "Actualizando UI con ${ausencias.size} items")
            ausenciasAdapter.actualizarLista(ausencias)
            // Mostrar mensaje si la lista está vacía
            binding.tvNoHayAusencias.visibility = if (ausencias.isEmpty() && !viewModel.isLoading.value!!) View.VISIBLE else View.GONE
        })
    }
    /*
     * Rellena el menú desplegable con los nombres de los empleados.
     */
    private fun setupDropdownMenu(empleados: List<EmpleadoSimpleDTO>) {
        val nombresEmpleados = mutableListOf("Todos los empleados")
        nombresEmpleados.addAll(empleados.map { it.nombre })

        empleadosAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nombresEmpleados)
        binding.filtroEmpleadoAutoComplete.setAdapter(empleadosAdapter)

        binding.filtroEmpleadoAutoComplete.setText("Todos los empleados", false)
    }
    /*
         * Configura el "oyente" para cuando el gestor selecciona un empleado del menú desplegable.
         */
    private fun setupDropdownListener() {
        binding.filtroEmpleadoAutoComplete.setOnItemClickListener { _, _, position, _ ->
            val seleccion = empleadosAdapter.getItem(position) as String
            val filtroNombre = if (seleccion == "Todos los empleados") null else seleccion
            viewModel.onFiltroCambiado(filtroNombre)
        }
    }
}