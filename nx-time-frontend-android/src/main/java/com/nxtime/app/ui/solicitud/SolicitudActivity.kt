package com.nxtime.app.ui.solicitud

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.databinding.ActivitySolicitudBinding
import java.time.LocalDate
import java.util.Calendar

/**
 * SolicitudActivity: Es la Activity que muestra el formulario para que un empleado pida una ausencia.
 */

class SolicitudActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySolicitudBinding

        /**
         * Inyecta el ViewModel usando una Factory.
         */

    private val viewModel: SolicitudViewModel by viewModels {
        SolicitudViewModelFactory((application as NxTimeApplication).authRepository)
    }

    // Almacena las fechas que el usuario selecciona en el calendario
    private var fechaInicioSeleccionada: LocalDate? = null
    private var fechaFinSeleccionada: LocalDate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySolicitudBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Configurar el menú desplegable (antes Spinner)
        setupDropdown()

        // 2. Configurar los listeners de los campos de fecha
        setupDatePickers()

        // 3. Configurar el listener del botón de enviar
        binding.btnEnviarSolicitud.setOnClickListener {
            enviarSolicitud()
        }

        // 4. Observar el estado de la solicitud
        viewModel.solicitudState.observe(this, Observer { state ->
            actualizarUI(state)
        })
    }

      /**
       * Rellena el menú desplegable con los valores del Enum 'TipoAusencia'
       */

    private fun setupDropdown() {

        val tiposDeAusencia = TipoAusencia.values().map { it.name }


        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposDeAusencia)


        binding.tipoAusenciaAutoComplete.setAdapter(adapter)
    }

    /**
     * Hace que los campos de texto de fecha abran el calendario al pulsarlos.
     */
    private fun setupDatePickers() {
        // Los TextInputEditText ahora son clicables (ver XML)
        binding.fechaInicioEditText.setOnClickListener {
            mostrarDatePicker(true)
        }
        binding.fechaFinEditText.setOnClickListener {
            mostrarDatePicker(false)
        }
    }

        /**
         * Muestra el pop-up del calendario. Cuando el usuario elige una fecha, la guarda en una variable y la escribe en el campo de texto.
         */

    private fun mostrarDatePicker(esFechaInicio: Boolean) {
        val calendario = Calendar.getInstance()
        val anio = calendario.get(Calendar.YEAR)
        val mes = calendario.get(Calendar.MONTH)
        val dia = calendario.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this,
            { _, anioSeleccionado, mesSeleccionado, diaSeleccionado ->

                val fecha = LocalDate.of(anioSeleccionado, mesSeleccionado + 1, diaSeleccionado)

                if (esFechaInicio) {
                    fechaInicioSeleccionada = fecha
                    binding.fechaInicioEditText.setText(fecha.toString())
                } else {
                    fechaFinSeleccionada = fecha
                    binding.fechaFinEditText.setText(fecha.toString())
                }
            }, anio, mes, dia
        )

        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

       /**
        * Se llama al pulsar "Enviar".
        */

    private fun enviarSolicitud() {

        val tipoString = binding.tipoAusenciaAutoComplete.text.toString()
        val motivo = binding.etMotivo.text.toString() // Este ID es el mismo

        // Validaciones
        if (tipoString.isEmpty()) {
            Toast.makeText(this, "Por favor, selecciona un tipo de ausencia", Toast.LENGTH_SHORT).show()
            return
        }
        val tipoSeleccionado = TipoAusencia.valueOf(tipoString)

        if (fechaInicioSeleccionada == null) {
            Toast.makeText(this, "Por favor, selecciona una fecha de inicio", Toast.LENGTH_SHORT).show()
            return
        }
        if (fechaFinSeleccionada == null) {
            Toast.makeText(this, "Por favor, selecciona una fecha de fin", Toast.LENGTH_SHORT).show()
            return
        }
        if (fechaInicioSeleccionada!!.isAfter(fechaFinSeleccionada)) {
            Toast.makeText(this, "La fecha de fin no puede ser anterior a la de inicio", Toast.LENGTH_SHORT).show()
            return
        }

        // Si todo es válido, llamamos al ViewModel
        viewModel.enviarSolicitud(fechaInicioSeleccionada!!, fechaFinSeleccionada!!, tipoSeleccionado, motivo)
    }

      /**
       * Actualiza la UI según el estado que le envía el ViewModel.
       */

    private fun actualizarUI(state: SolicitudState) {

        when (state) {
            is SolicitudState.Loading -> {
                binding.btnEnviarSolicitud.isEnabled = false
                binding.btnEnviarSolicitud.text = "Enviando..."
            }
            is SolicitudState.Success -> {
                binding.btnEnviarSolicitud.isEnabled = true
                binding.btnEnviarSolicitud.text = "Enviar Solicitud"
                Toast.makeText(this, "¡Solicitud enviada con éxito!", Toast.LENGTH_LONG).show()
                finish()
            }
            is SolicitudState.Error -> {
                binding.btnEnviarSolicitud.isEnabled = true
                binding.btnEnviarSolicitud.text = "Enviar Solicitud"
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}