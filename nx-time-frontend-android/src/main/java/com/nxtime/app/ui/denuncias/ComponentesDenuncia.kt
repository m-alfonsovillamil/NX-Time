package com.nxtime.app.ui.denuncias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import com.nxtime.app.data.dto.MensajeDenunciaDTO
import com.nxtime.app.data.dto.ResumenDenunciaDTO
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats

/**
 * Lo que comparten las dos pantallas del canal (Fase G): la fila de la
 * lista, el aviso de plazo y el globo de un mensaje.
 *
 * Están juntas porque el denunciante y quien instruye miran **el mismo
 * expediente**, y que se pinte igual a los dos no es una casualidad que
 * convenga mantener a mano: si la conversación se viera distinta según
 * quién la abre, la pantalla estaría diciendo algo sobre quién es cada
 * cual.
 */

/**
 * Una fila de la lista.
 *
 * Enseña la categoría, el estado y el plazo que más apremia — nunca la
 * descripción, que ni siquiera viaja en el resumen: una lista se mira de
 * refilón, a veces con alguien detrás.
 */
@Composable
fun FilaDenuncia(
    resumen: ResumenDenunciaDTO,
    onAbrir: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado = EstadoDenuncia.de(resumen.estado)

    Card(
        onClick = onAbrir,
        modifier = modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = resumen.categoriaEtiqueta,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                estado?.let {
                    AssistChip(
                        onClick = onAbrir,
                        label = { Text(stringResource(it.etiqueta)) },
                        colors = if (it.estaAbierta) {
                            AssistChipDefaults.assistChipColors()
                        } else {
                            AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (resumen.anonima) R.string.denuncia_presentada_anonima
                    else R.string.denuncia_presentada_identificada,
                    DateFormats.fechaLarga(resumen.creadoEn)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (resumen.mensajes > 0) {
                Text(
                    text = stringResource(R.string.denuncia_mensajes_cuenta, resumen.mensajes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AvisoDePlazo(
                diasHastaAcuse = resumen.diasHastaAcuse,
                diasHastaRespuesta = resumen.diasHastaRespuesta
            )
        }
    }
}

/**
 * El plazo legal que más apremia, o nada si no queda ninguno vivo.
 *
 * **Solo se enseña uno.** Los dos plazos del art. 9.2 corren a la vez y
 * el de los 7 días vence mucho antes, así que pintarlos juntos deja al
 * lado el número urgente y el que sobra, y la vista se acostumbra a no
 * leer ninguno.
 *
 * Un plazo pasado llega en negativo y se pinta en rojo con su texto
 * propio: esconder un incumplimiento tras un cero es lo contrario de
 * para lo que existe el contador.
 */
@Composable
fun AvisoDePlazo(
    diasHastaAcuse: Long?,
    diasHastaRespuesta: Long?,
    modifier: Modifier = Modifier
) {
    val (texto, vencido) = when {
        diasHastaAcuse != null && diasHastaAcuse < 0 ->
            stringResource(R.string.denuncia_plazo_acuse_vencido, -diasHastaAcuse) to true

        diasHastaAcuse != null ->
            stringResource(R.string.denuncia_plazo_acuse, diasHastaAcuse) to false

        diasHastaRespuesta != null && diasHastaRespuesta < 0 ->
            stringResource(R.string.denuncia_plazo_respuesta_vencido, -diasHastaRespuesta) to true

        diasHastaRespuesta != null ->
            stringResource(R.string.denuncia_plazo_respuesta, diasHastaRespuesta) to false

        // Ni acuse pendiente ni expediente abierto: no hay nada que
        // contar, y un "—" solo añadiría ruido.
        else -> return
    }

    Spacer(Modifier.height(4.dp))
    Text(
        text = texto,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (vencido) FontWeight.Bold else FontWeight.Normal,
        color = if (vencido) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
    )
}

/**
 * Un mensaje de la conversación.
 *
 * De qué lado se pinta lo decide **`autorRol`, nunca `autor`**. En una
 * denuncia anónima el mensaje del denunciante llega sin nombre, así que
 * mirar el nombre ataría la presentación al anonimato: bastaría con que
 * un expediente anónimo y otro identificado se vieran distinto para que
 * la pantalla estuviera diciendo cuál es cuál.
 */
@Composable
fun MensajeDeDenuncia(mensaje: MensajeDenunciaDTO, modifier: Modifier = Modifier) {
    val delInstructor = AutorMensaje.de(mensaje.autorRol) == AutorMensaje.INSTRUCTOR

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (delInstructor) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            elevation = elevacionDeTarjeta(),
            colors = CardDefaults.cardColors(
                containerColor = if (delInstructor) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    // El nombre solo cuando lo hay. Su ausencia no se
                    // rellena con "Anónimo": el rol ya dice quién habla,
                    // y una etiqueta de más invita a buscarle sentido.
                    text = mensaje.autor
                        ?: stringResource(
                            if (delInstructor) R.string.denuncia_autor_instructor
                            else R.string.denuncia_autor_denunciante
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(text = mensaje.texto, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = DateFormats.fechaYHora(mensaje.creadoEn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
