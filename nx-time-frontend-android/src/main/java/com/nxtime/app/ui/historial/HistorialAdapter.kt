package com.nxtime.app.ui.historial

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.databinding.ItemHistorialBinding
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale


/*
 * HistorialAdapter: Es el "cerebro" de la lista (RecyclerView). Coge la lista de fichajes y los convierte en vistas para mostrar en la pantalla del empleado.
 */

class HistorialAdapter(
    private var registros: List<Registro>
) : RecyclerView.Adapter<HistorialAdapter.HistorialViewHolder>() {

    /*
     * Formateadores para convertir el texto de la API en formatos legibles
     */

    private val isoParser = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val fechaFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM, yyyy", Locale("es", "ES"))
    private val horaFormatter = DateTimeFormatter.ofPattern("HH:mm 'h'", Locale("es", "ES"))

    /*
     * HistorialViewHolder: Representa uan sola fila en la lista. Mantiene una referencia a las vistas de 'item_historial.xml'.
     */

    inner class HistorialViewHolder(private val binding: ItemHistorialBinding) :
        RecyclerView.ViewHolder(binding.root) {

            /*
             * Esta función inyecta los datos de un 'registro' en los TextViews de la tarjeta
             */

        fun bind(registro: Registro) {
            binding.tvFecha.text = formatarFecha(registro.horaEntrada)
            binding.tvEntradaValor.text = formatarHora(registro.horaEntrada)
            binding.tvSalidaValor.text = formatarHora(registro.horaSalida)

            /*
              * Llama a la función de cálculo para obtener las horas trabajadas (restando las pausas).
              */

            binding.tvTotalHoras.text = calcularTotalHoras(
                registro.horaEntrada,
                registro.horaSalida,
                registro.minutosPausaAcumulados
            )
        }
    }

    /*
     * Se llama cuando el RecyclerView necesita crear una nueva tarjeta. Crea el XML 'item_historial.xml'.
     */

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistorialViewHolder {
        val binding = ItemHistorialBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistorialViewHolder(binding)
    }

    /*
     * Devuelve el número total de items que hay en la lista.
     */

    override fun getItemCount(): Int = registros.size

    /*
     * Se llama para "pintar" los datos en una tarjeta específica.
     */

    override fun onBindViewHolder(holder: HistorialViewHolder, position: Int) { holder.bind(registros[position]) }

    /*
     * Función pública que usa la Activity para actualizar la lista de datos y forzar que el RecyclerView se redibuje.
     */

    fun actualizarLista(nuevaLista: List<Registro>) { this.registros = nuevaLista; notifyDataSetChanged() }

        /*
         * Funciones privadas de ayuda para formatear las fechas y horas.
         */
    private fun formatarFecha(rawDateTime: String?): String {
        if (rawDateTime == null) return "Sin fecha"
        return try {
            val dateTime = LocalDateTime.parse(rawDateTime, isoParser)
            dateTime.format(fechaFormatter)
        } catch (e: DateTimeParseException) { "Fecha inválida" }
    }
    private fun formatarHora(rawDateTime: String?): String {
        if (rawDateTime == null) return "---"
        return try {
            val dateTime = LocalDateTime.parse(rawDateTime, isoParser)
            dateTime.format(horaFormatter)
        } catch (e: DateTimeParseException) { "Hora inválida" }
    }

       /*
        * Función de lógica de negocio para calcular las horas netas trabajadas.
        */
    private fun calcularTotalHoras(entrada: String?, salida: String?, minutosPausa: Long): String {
        if (entrada == null || salida == null) return "---"

        return try {
            val entradaTime = LocalDateTime.parse(entrada, isoParser)
            val salidaTime = LocalDateTime.parse(salida, isoParser)


            val duracionBruta = Duration.between(entradaTime, salidaTime)


            val duracionNeta = duracionBruta.minusMinutes(minutosPausa)


            val totalMinutosNetos = duracionNeta.toMinutes()
            val horas = totalMinutosNetos / 60
            val minutos = totalMinutosNetos % 60

            "${horas}h ${minutos}min"

        } catch (e: Exception) { "Error" }
    }
}