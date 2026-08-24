package com.nxtime.app.ui.gestor.historial

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
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.databinding.ActivityGestorHistorialBinding


/*
 * Activity para que el gestor vea el historial de fichajes de su equipo y pueda filtrarlo.
 */

class GestorHistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestorHistorialBinding
    private lateinit var historialAdapter: GestorHistorialAdapter


    private lateinit var empleadosAdapter: ArrayAdapter<String>

        /*
         * Inyecta el ViewModel usando una Factory.
         */
    private val viewModel: GestorHistorialViewModel by viewModels {
        GestorHistorialViewModelFactory((application as NxTimeApplication).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestorHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Configurar el RecyclerView (igual que antes)
        setupRecyclerView()

        // 2. Configurar los Observadores
        setupObservers()

        // 3. Configurar el listener del desplegable
        setupDropdownListener()

        // 4. Cargar los datos
        viewModel.cargarDatos()
    }
        /*
         * Prepara el RecyclerView
         */
    private fun setupRecyclerView() {
        historialAdapter = GestorHistorialAdapter(emptyList())
        binding.rvHistorialGestor.apply {
            layoutManager = LinearLayoutManager(this@GestorHistorialActivity)
            adapter = historialAdapter
        }
    }

    /**
     * Observa los LiveData del ViewModel y reacciona a los cambios.
     */
    private fun setupObservers() {
        // Observa el estado de "Cargando"
        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.historialProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.rvHistorialGestor.visibility = if (isLoading) View.GONE else View.VISIBLE
        })

        // Observa si hay errores
        viewModel.error.observe(this, Observer { error ->
            if (error != null) {
                Log.e("GestorHistorial", "Error: $error")
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        })

        // Observa la lista de empleados (para el desplegable)
        viewModel.empleados.observe(this, Observer { empleados ->
            Log.d("GestorHistorial", "Empleados recibidos: ${empleados.size}")
            setupDropdownMenu(empleados)
        })

        // Observa la lista filtrada de historial (para el RecyclerView)
        viewModel.historialFiltrado.observe(this, Observer { historial ->
            Log.d("GestorHistorial", "Actualizando UI con ${historial.size} items")
            historialAdapter.actualizarLista(historial)
            // TODO: Mostrar un mensaje si 'historial' está vacío
        })
    }

    /**
     * Rellena el menú desplegable con la lista de empleados.
     */
    private fun setupDropdownMenu(empleados: List<EmpleadoSimpleDTO>) {

        val nombresEmpleados = mutableListOf("Todos los empleados")

        nombresEmpleados.addAll(empleados.map { it.nombre })


        empleadosAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nombresEmpleados)
        binding.filtroEmpleadoAutoComplete.setAdapter(empleadosAdapter)

        // Ponemos "Todos los empleados" como valor por defecto
        binding.filtroEmpleadoAutoComplete.setText("Todos los empleados", false)
    }

    /**
     * Escucha los clics en el menú desplegable.
     */
    private fun setupDropdownListener() {

        binding.filtroEmpleadoAutoComplete.setOnItemClickListener { _, _, position, _ ->
            val seleccion = empleadosAdapter.getItem(position) as String

            val filtroNombre = if (seleccion == "Todos los empleados") {
                null
            } else {
                seleccion
            }

            viewModel.onFiltroCambiado(filtroNombre)
        }
    }
}