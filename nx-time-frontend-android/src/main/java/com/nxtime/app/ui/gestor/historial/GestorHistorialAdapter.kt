package com.nxtime.app.ui.gestor.historial

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nxtime.app.data.dto.RegistroEquipoDTO
import com.nxtime.app.databinding.ItemGestorHistorialBinding
import java.time.Duration
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/*
 * GestorHistorialAdapter: Es el "cerebro" del RecyclerView. Sabe cómo coger la lista de fichajes del equipo y convertirlos en vistas para mostrar en la pantalla del gestor.
 */
class GestorHistorialAdapter(
    private var registros: List<RegistroEquipoDTO>
) : RecyclerView.Adapter<GestorHistorialAdapter.HistorialViewHolder>() {

    /*
     * Formateadores de fecha/hora para convertir el texto de la API en un formato legible
     */

    private val fechaParser = DateTimeFormatter.ISO_LOCAL_DATE
    private val horaParser = DateTimeFormatter.ISO_LOCAL_TIME
    private val fechaFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM, yyyy", Locale("es", "ES"))
    private val horaFormatter = DateTimeFormatter.ofPattern("HH:mm 'h'", Locale("es", "ES"))

    /*
     * HistorialViewHolder: Representa una sola fila (item) en la lista. Mantiene una referencia a las vistas de un 'item_gestor_historial.xml' gracias al ViewBinding.
     */

    inner class HistorialViewHolder(private val binding: ItemGestorHistorialBinding) :
        RecyclerView.ViewHolder(binding.root) {

                /*
                 * Esta función inyecta los datos de un registro en los TextViews de la tarjeta.
                 */
        fun bind(registro: RegistroEquipoDTO) {
            binding.tvNombreEmpleado.text = registro.usuario.nombre
            binding.tvFecha.text = formatarFecha(registro.fecha)
            binding.tvEntradaValor.text = formatarHora(registro.horaEntrada)
            binding.tvSalidaValor.text = formatarHora(registro.horaSalida)

                    /*
                     * Llama a la función de cálculo para obtener las horas trabajadas, restando las pausas.
                     */
            binding.tvTotalHoras.text = calcularTotalHoras(
                registro.horaEntrada,
                registro.horaSalida,
                registro.minutosPausaAcumulados
            )
        }
    }

        /*
         * Se llama cuando el RecyclerView necesita crear una nueva tarjeta (ViewHolder). Crea el XML 'item_gestor_historial.xml'.
         */

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistorialViewHolder {
        val binding = ItemGestorHistorialBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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

    fun actualizarLista(nuevaLista: List<RegistroEquipoDTO>) { this.registros = nuevaLista; notifyDataSetChanged() }


      /*
       * Funciones privadas de ayuda para formatear las fechas y horas.
       */
    private fun formatarFecha(rawFecha: String?): String {
        if (rawFecha == null) return "Sin fecha"
        return try {
            val fecha = LocalDate.parse(rawFecha, fechaParser)
            fecha.format(fechaFormatter)
        } catch (e: DateTimeParseException) { rawFecha }
    }
    private fun formatarHora(rawHora: String?): String {
        if (rawHora == null) return "---"
        return try {
            val hora = LocalTime.parse(rawHora, horaParser)
            hora.format(horaFormatter)
        } catch (e: DateTimeParseException) { rawHora }
    }

        /*
         * Función de lógica de negocio para calcular las horas netas trabajadas.
         */
    private fun calcularTotalHoras(entrada: String?, salida: String?, minutosPausa: Long): String {
        if (entrada == null || salida == null) return "---"

        return try {
            val entradaTime = LocalTime.parse(entrada, horaParser)
            val salidaTime = LocalTime.parse(salida, horaParser)


            val duracionBruta = Duration.between(entradaTime, salidaTime)


            val duracionNeta = duracionBruta.minusMinutes(minutosPausa)


            val totalMinutosNetos = duracionNeta.toMinutes()
            val horas = totalMinutosNetos / 60
            val minutos = totalMinutosNetos % 60

            "${horas}h ${minutos}min"

        } catch (e: Exception) { "Error" }
    }
}