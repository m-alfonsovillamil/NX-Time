package com.nxtime.app.ui.gestor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.databinding.ItemGestorAusenciaBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/*
 * GestorAusenciasAdapter: Es el "cerebro" de la lista para la pantalla de "Solicitudes Pendientes" del gestor.
 */

class GestorAusenciasAdapter(
    private var peticiones: List<RespuestaAusencia>,
    private val onAprobarClicked: (RespuestaAusencia) -> Unit,
    private val onRechazarClicked: (RespuestaAusencia) -> Unit
) : RecyclerView.Adapter<GestorAusenciasAdapter.GestorAusenciaViewHolder>() {

    /*
     * Formateadores para convertir la fecha de la API a un formato legible.
     */

    private val fechaParser = DateTimeFormatter.ISO_LOCAL_DATE
    private val fechaFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "ES"))


    /*
     * GestorAusenciaViewHolder: Mantiene una referencia a las vistas de 'item_gestor_ausencia.xml'.
     */
    inner class GestorAusenciaViewHolder(private val binding: ItemGestorAusenciaBinding) : RecyclerView.ViewHolder(binding.root) {

        /*
         * Esta función inyecta los datos de una 'peticion' en los TextViews de la tarjeta (item).
         */

        fun bind(peticion: RespuestaAusencia) {
            binding.tvNombreEmpleado.text = peticion.usuario.nombre
            binding.tvTipo.text = peticion.tipo.name


            val fechaInicioF = formatarFecha(peticion.fechaInicio)
            val fechaFinF = formatarFecha(peticion.fechaFin)
            binding.tvFechas.text = "Del $fechaInicioF al $fechaFinF"

            if (peticion.motivo.isNullOrBlank()) {
                binding.tvMotivo.visibility = View.GONE
            } else {
                binding.tvMotivo.visibility = View.VISIBLE
                binding.tvMotivo.text = "Motivo: ${peticion.motivo}"
            }

              /*
               * Configura los "oyentes" de los botones. Cuando se hace clic, llaman a la acción que la Activity nos pasó en el constructor.
               */

            binding.btnAprobar.setOnClickListener {
                onAprobarClicked(peticion)
            }
            binding.btnRechazar.setOnClickListener {
                onRechazarClicked(peticion)
            }
        }
    }

        /*
         * Se llama cuando el RecyclerView necesita crear una nueva tarjeta. Crea el XML 'item_gestor_ausencia.xml'.
         */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GestorAusenciaViewHolder {
        val binding = ItemGestorAusenciaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GestorAusenciaViewHolder(binding)
    }

    /*
     * Devuelve el número total de items que hay en la lista.
     */
    override fun getItemCount(): Int = peticiones.size

        /*
         * Se llama para inyectar los datos en una tarjeta específica.
         */
    override fun onBindViewHolder(holder: GestorAusenciaViewHolder, position: Int) {
        holder.bind(peticiones[position])
    }

    /*
     * Función pública que usa la Activity para actualizar la lista de datos y forzar que el RecyclerView se redibuje.
     */

    fun actualizarLista(nuevaLista: List<RespuestaAusencia>) {
        this.peticiones = nuevaLista
        notifyDataSetChanged()
    }

        /*
         * Función privada de ayuda para formatear la fecha.
         */

    private fun formatarFecha(rawFecha: String?): String {
        if (rawFecha == null) return "---"
        return try {
            val fecha = LocalDate.parse(rawFecha, fechaParser)
            fecha.format(fechaFormatter)
        } catch (e: DateTimeParseException) {
            rawFecha
        }
    }
}