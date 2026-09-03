package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.R
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.ui.theme.LocalColoresJornada
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.etiqueta

/**
 * Una petición de ausencia, tal como la ven tanto el empleado que la
 * pidió como el gestor que la resuelve.
 *
 * Es una sola tarjeta para los dos casos porque los dos adaptadores
 * anteriores (`AusenciasAdapter` y `GestorAusenciasAdapter`) pintaban
 * casi lo mismo con código distinto, y por eso se les habían ido
 * separando los detalles: solo uno enseñaba el motivo, y **ninguno**
 * enseñaba el comentario con el que el gestor justifica un rechazo,
 * aunque el backend lo exija al rechazar y lo devuelva en cada consulta.
 *
 * @param mostrarEmpleado el gestor necesita saber de quién es cada
 *   petición; al empleado, en su propia lista, ese dato le sobra.
 * @param acciones botones que solo tienen sentido en la lista del
 *   gestor (aprobar y rechazar). Vacío en la del empleado.
 */
@Composable
fun TarjetaAusencia(
    peticion: RespuestaAusencia,
    modifier: Modifier = Modifier,
    mostrarEmpleado: Boolean = false,
    acciones: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(peticion.tipo.etiqueta),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                EtiquetaEstado(peticion.estado)
            }

            if (mostrarEmpleado) {
                Text(
                    text = peticion.usuario.nombre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.ausencias_rango,
                    DateFormats.fechaCorta(peticion.fechaInicio),
                    DateFormats.fechaCorta(peticion.fechaFin)
                ),
                style = MaterialTheme.typography.bodyLarge
            )

            if (peticion.diasHabiles > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.ausencias_dias_habiles,
                        peticion.diasHabiles,
                        peticion.diasHabiles
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            peticion.motivo?.takeIf { it.isNotBlank() }?.let { motivo ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ausencias_motivo, motivo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            peticion.aprobadoPor?.let { quien ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ausencias_resuelta_por, quien.nombre),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            /*
             * El motivo del rechazo. Es la mitad que faltaba del trabajo
             * de la Fase 9 del backend: allí se hizo obligatorio para
             * poder rechazar, y aquí no se enseñaba en ninguna pantalla,
             * así que el empleado veía "Rechazada" y nada más.
             */
            peticion.comentarioResolucion?.takeIf { it.isNotBlank() }?.let { comentario ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ausencias_comentario, comentario),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            acciones()
        }
    }
}

/**
 * El estado, con color.
 *
 * Se reutilizan los colores de jornada porque significan lo mismo aquí:
 * verde para lo que sigue adelante, ámbar para lo que está esperando,
 * rojo para lo que se ha parado. Ya están definidos para tema claro y
 * oscuro, así que no hay ningún color fijo que se quede ilegible al
 * cambiar de tema -- que era el defecto de la versión anterior.
 */
@Composable
private fun EtiquetaEstado(estado: EstadoAusencia) {
    val colores = LocalColoresJornada.current
    val fondo: Color
    val contenido: Color
    when (estado) {
        EstadoAusencia.PENDIENTE -> {
            fondo = colores.enPausa; contenido = colores.onEnPausa
        }
        EstadoAusencia.APROBADA -> {
            fondo = colores.trabajando; contenido = colores.onTrabajando
        }
        EstadoAusencia.RECHAZADA -> {
            fondo = colores.parado; contenido = colores.onParado
        }
    }

    Surface(
        color = fondo,
        contentColor = contenido,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = stringResource(estado.etiqueta),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
