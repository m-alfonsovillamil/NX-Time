package com.nxtime.app.ui.ausencias

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nxtime.app.R
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.databinding.ItemAusenciaBinding

/*
 * Coge la lista de datos (peticiones) y los convierte en vistas (tarjetas) para mostrar en pantalla.
 */
class AusenciasAdapter(
    private var peticiones: List<RespuestaAusencia>
) : RecyclerView.Adapter<AusenciasAdapter.AusenciaViewHolder>() {

    /*
     * Mantiene una referencia a las vistas de un 'item_ausencia.xml' gracias al ViewBinding.
     */
    inner class AusenciaViewHolder(private val binding: ItemAusenciaBinding) : RecyclerView.ViewHolder(binding.root) {

        /*
         * Esta función "pinta" los datos de una 'peticion' en los TextViews de la tarjeta (item).
         */
        fun bind(peticion: RespuestaAusencia) {

            binding.tvTipo.text = peticion.tipo.name
            binding.tvEstado.text = peticion.estado.name
            binding.tvFechas.text = "Del ${peticion.fechaInicio} al ${peticion.fechaFin}"
            binding.tvNombreEmpleado.text = peticion.usuario.nombre


            val context = binding.root.context

            val colorRes = when (peticion.estado) {
                EstadoAusencia.APROBADA -> R.color.color_fichar_verde
                EstadoAusencia.RECHAZADA -> R.color.color_fichar_rojo
                else -> R.color.color_estado_pendiente // Asegúrate de haber añadido este en colors.xml
            }

            binding.tvEstado.setTextColor(ContextCompat.getColor(context, colorRes))


            if (peticion.motivo.isNullOrBlank()) {
                binding.tvMotivo.visibility = View.GONE
            } else {
                binding.tvMotivo.visibility = View.VISIBLE
                binding.tvMotivo.text = "Motivo: ${peticion.motivo}"
            }
        }
    }

    /*
     * Crea el XML 'item_ausencia.xml' y lo devuelve.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AusenciaViewHolder {
        val binding = ItemAusenciaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AusenciaViewHolder(binding)
    }

    /*
     * Devuelve el número total de items que hay en la lista.
     */
    override fun getItemCount(): Int = peticiones.size

    /*
     * Coge los datos de la 'position' (fila) y llama a 'holder.bind()' para rellenar esa tarjeta.
     */
    override fun onBindViewHolder(holder: AusenciaViewHolder, position: Int) {
        holder.bind(peticiones[position])
    }

    /*
     * Función pública que usamos desde la Activity para cambiar la lista de datos
     */
    fun actualizarLista(nuevaLista: List<RespuestaAusencia>) {
        this.peticiones = nuevaLista
        notifyDataSetChanged()
    }
}